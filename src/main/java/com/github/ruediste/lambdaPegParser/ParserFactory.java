package com.github.ruediste.lambdaPegParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.RemappingMethodAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import com.github.ruediste.lambdaPegParser.weaving.LocalVariableShifter;
import com.github.ruediste.lambdaPegParser.weaving.MethodCallInliner;
import com.github.ruediste.lambdaPegParser.weaving.MinMaxLineMethodAdapter;
import com.github.ruediste.lambdaPegParser.weaving.PrototypeCustomizer;
import com.google.common.io.ByteStreams;
import com.google.common.reflect.TypeToken;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * Factory for instances of parser classes, derived from {@link Parser}.
 */
public class ParserFactory {

    public static class WeavedClassLoader extends ClassLoader {

        private String parserClassName;
        private byte[] weavedByteCode;

        public WeavedClassLoader(ClassLoader parent, String parserClassName, byte[] weavedByteCode) {
            super(parent);
            this.parserClassName = parserClassName;
            this.weavedByteCode = weavedByteCode;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {

            if (name.equals(parserClassName)) {
                defineClass(parserClassName, weavedByteCode, 0, weavedByteCode.length);
            }

            if (name.startsWith(parserClassName) && name.substring(parserClassName.length()).startsWith("$")) {
                InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class");
                byte[] bb;
                try {
                    bb = ByteStreams.toByteArray(in);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return defineClass(name, bb, 0, bb.length);
            }
            return super.loadClass(name);
        }
    }

    /**
     * Instantiate a weaved instance of a parser class and return it as instance
     * of an interface implemented by the parser. A fresh {@link ParsingContext}
     * is created with the supplied input.
     */
    public static <C extends ParsingContext<?>, T extends Parser<C>, I> I create(Class<T> parserClass,
            Class<I> intrface, String input) {
        return create(parserClass, intrface, createParsingContext(parserClass).apply(input));
    }

    /**
     * Instantiate a weaved instance of a parser class and return it as instance
     * of an interface implemented by the parser.
     */
    public static <C extends ParsingContext<?>, T extends Parser<C>, I> I create(Class<? extends T> cls,
            Class<I> intrface, C ctx) {
        return create(cls, intrface).apply(ctx);
    }

    private static <C extends ParsingContext<?>, T extends Parser<C>, I> Function<C, I> create(Class<T> cls,
            Class<I> intrface) {
        Function<ParsingContext<?>, Object> func = instantiateWeavedParser(cls);
        return ctx -> intrface.cast(func.apply(ctx));
    }

    /**
     * Instantiate a weaved instance of a parser class and return a proxy
     * delegating to the instantiated parser. A fresh {@link ParsingContext} is
     * created with the supplied input.
     */
    public static <C extends ParsingContext<?>, T extends Parser<C>> T create(Class<T> cls, String input) {
        C ctx = createParsingContext(cls).apply(input);
        return create(cls, ctx);
    }

    public static <C extends ParsingContext<?>, T extends Parser<C>> C createParsingContext(Class<T> parserClass,
            String input) {
        return createParsingContext(parserClass).apply(input);
    }

    /**
     * Create a parsing context for the given parserClass and input. The
     * instantiated type is based on the generic type information of the parser
     * class. The context has to have a constructor with a single {@link String}
     * parameter.
     */
    @SuppressWarnings("unchecked")
    public static <C extends ParsingContext<?>, T extends Parser<C>> Function<String, C> createParsingContext(
            Class<T> parserClass) {
        try {
            Constructor<?> constructor = getParsingContextType(parserClass).getConstructor(String.class);
            return input -> {
                try {
                    return (C) constructor.newInstance(input);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Error while creating instance of parser context", e);
                }
            };
        } catch (IllegalArgumentException | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Error while instantiating parsing context", e);
        }
    }

    public static <C extends ParsingContext<?>, T extends Parser<C>> T create(Class<T> cls, C ctx) {
        return create(cls).apply(ctx);
    }

    /**
     * Instantiate a weaved instance of a parser class and return a proxy
     * delegating to the instantiated parser.
     */
    public static <C extends ParsingContext<?>, T extends Parser<C>> Function<C, T> create(Class<T> cls) {
        Function<ParsingContext<?>, Object> weavedParserFactory = instantiateWeavedParser(cls);
        Class<?> parsingContextType = getParsingContextType(cls);

        return ctx -> {
            Object weavedParser = weavedParserFactory.apply(ctx);
            Enhancer e = new Enhancer();
            e.setSuperclass(cls);

            e.setCallback(new MethodInterceptor() {

                @Override
                public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                    Method m = findMethod(weavedParser.getClass().getMethods(), method.getName(),
                            method.getParameterTypes());
                    if (m == null) {
                        m = findMethod(weavedParser.getClass(), method.getName(), method.getParameterTypes());
                    }
                    m.setAccessible(true);
                    try {
                        return m.invoke(weavedParser, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }

                private Method findMethod(Class<?> cls, String methodName, Class<?>[] parameterTypes)
                        throws NoSuchMethodException, SecurityException {
                    if (cls == null)
                        return null;
                    Method m = findMethod(cls.getDeclaredMethods(), methodName, parameterTypes);
                    return m == null ? findMethod(cls.getSuperclass(), methodName, parameterTypes) : m;
                }

                private Method findMethod(Method[] candidates, String methodName, Class<?>[] parameterTypes) {
                    for (Method m : candidates) {
                        if (methodName.equals(m.getName()) && Arrays.equals(parameterTypes, m.getParameterTypes()))
                            return m;
                    }
                    return null;
                }
            });

            return cls.cast(e.create(new Class[] { parsingContextType }, new Object[] { ctx }));
        };

    }

    private static Function<ParsingContext<?>, Object> instantiateWeavedParser(Class<?> parserClass) {
        try {
            String parserClassName = parserClass.getName();
            Class<?> weavedClass = new WeavedClassLoader(parserClass.getClassLoader(), parserClassName,
                    weaveClass(parserClass)).loadClass(parserClassName);
            Constructor<?> constructor = weavedClass.getConstructor(getParsingContextType(weavedClass));
            constructor.setAccessible(true);
            return ctx -> {
                try {
                    return constructor.newInstance(ctx);
                } catch (Exception e) {
                    throw new RuntimeException("Error while instantiating parser class", e);
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalArgumentException e) {
            throw new RuntimeException("Error while weaving and instantiating parser class", e);
        }
    }

    private static Class<?> getParsingContextType(Class<?> parserClass) {
        return TypeToken.of(parserClass).resolveType(Parser.class.getTypeParameters()[0]).getRawType();
    }

    private static byte[] weaveClass(Class<?> parserClass) {
        String internalParserClassName = parserClass.getName().replace('.', '/');
        InputStream in = parserClass.getClassLoader().getResourceAsStream(internalParserClassName + ".class");
        ClassReader classReader;
        try {
            classReader = new ClassReader(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // load class
        ClassNode cn = new ClassNode();
        classReader.accept(cn, ClassReader.EXPAND_FRAMES);

        // dump raw byte code
        // cn.accept(new TraceClassVisitor(null, new Textifier(), new
        // PrintWriter(
        // System.out)));

        // load prototype method
        MethodNode prototype = loadPrototypeMethodNode();

        // modify methods
        for (int i = 0; i < cn.methods.size(); i++) {
            MethodNode ruleNode = cn.methods.get(i);
            if ("<init>".equals(ruleNode.name))
                continue;
            if ((Opcodes.ACC_STATIC & ruleNode.access) != 0)
                continue;
            if ((Opcodes.ACC_SYNTHETIC & ruleNode.access) != 0)
                continue;
            boolean memo = false;
            if (ruleNode.visibleAnnotations != null) {
                if (ruleNode.visibleAnnotations.stream().anyMatch(x -> Type.getDescriptor(NoRule.class).equals(x.desc)))
                    continue;
                memo = ruleNode.visibleAnnotations.stream()
                        .anyMatch(x -> Type.getDescriptor(Memo.class).equals(x.desc));
            }

            // get minimum and maximum line numbers
            MinMaxLineMethodAdapter minMaxLineMethodAdapter = new MinMaxLineMethodAdapter(Opcodes.ASM5, null);
            ruleNode.accept(minMaxLineMethodAdapter);

            // prepare the new method node
            MethodNode newNode;
            {
                String[] exceptions = ruleNode.exceptions.toArray(new String[] {});
                newNode = new MethodNode(ruleNode.access, ruleNode.name, ruleNode.desc, ruleNode.signature, exceptions);
            }

            MethodVisitor mv = newNode;

            // replace remaining references to PrototypeParser
            mv = new RemappingMethodAdapter(ruleNode.access, ruleNode.desc, mv,
                    new SimpleRemapper(PrototypeParser.class.getName().replace('.', '/'), internalParserClassName));

            // inline the call to the sampleRule method, replace it with the
            // original rule method
            mv = new MethodCallInliner(mv, ruleNode, minMaxLineMethodAdapter);

            // customize the code found in the prototype
            mv = new PrototypeCustomizer(mv, ruleNode, i, memo);

            // shift local variables to make space for parameters of the rule
            // method
            mv = new LocalVariableShifter(Type.getArgumentTypes(ruleNode.desc).length, prototype.access, prototype.desc,
                    mv);

            // trigger the transformation
            prototype.instructions.resetLabels();
            prototype.accept(mv);

            // replace the existing method
            cn.methods.set(i, newNode);
        }

        // dump weaved byte code
        // cn.accept(new TraceClassVisitor(null, new Textifier(), new
        // PrintWriter(
        // System.out)));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);

        byte[] b = cw.toByteArray();

        // verify weaved byte code
        PrintWriter pw = new PrintWriter(System.out);
        CheckClassAdapter.verify(new ClassReader(b), false, pw);

        return b;
    }

    private static MethodNode loadPrototypeMethodNode() {
        InputStream in = ParserFactory.class.getClassLoader()
                .getResourceAsStream(PrototypeParser.class.getName().replace('.', '/') + ".class");
        ClassReader classReader;
        try {
            classReader = new ClassReader(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
        Iterator<?> it = classNode.methods.iterator();
        while (it.hasNext()) {
            MethodNode node = (MethodNode) it.next();
            if ("prototypeAdvice".equals(node.name)) {
                return node;
            }
        }
        throw new RuntimeException("Prototype method not found");
    }
}
