package com.github.ruediste1.lambdaPegParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.RemappingMethodAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import com.github.ruediste1.lambdaPegParser.weaving.LocalVariableShifter;
import com.github.ruediste1.lambdaPegParser.weaving.MethodCallInliner;
import com.github.ruediste1.lambdaPegParser.weaving.MinMaxLineMethodAdapter;
import com.github.ruediste1.lambdaPegParser.weaving.PrototypeCustomizer;
import com.google.common.reflect.TypeToken;

/**
 * Factory for instances of parser classes, derived from {@link Parser}.
 */
public class ParserFactory {

	public static class WeavedClassLoader extends ClassLoader {

		private String parserClassName;
		private byte[] weavedByteCode;

		public WeavedClassLoader(ClassLoader parent, String parserClassName,
				byte[] weavedByteCode) {
			super(parent);
			this.parserClassName = parserClassName;
			this.weavedByteCode = weavedByteCode;
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {

			if (name.equals(parserClassName)) {
				defineClass(parserClassName, weavedByteCode, 0,
						weavedByteCode.length);
			}
			return super.loadClass(name);
		}
	}

	/**
	 * Instantiate a weaved instance of a parser class and return it as instance
	 * of an interface implemented by the parser. A fresh {@link ParsingContext}
	 * is created with the supplied input.
	 */
	public static <T> T create(Class<? extends T> parserClass,
			Class<T> intrface, String input) {
		return create(parserClass, intrface,
				createParsingContext(parserClass, input));
	}

	/**
	 * Instantiate a weaved instance of a parser class and return it as instance
	 * of an interface implemented by the parser.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T create(Class<? extends T> cls, Class<T> intrface,
			ParsingContext<?> ctx) {
		return (T) instantiateWeavedParser(ctx, cls.getName());
	}

	/**
	 * Instantiate a weaved instance of a parser class and return a proxy
	 * delegating to the instantiated parser. A fresh {@link ParsingContext} is
	 * created with the supplied input.
	 */
	public static <T extends Parser<?>> T create(Class<T> cls, String input) {
		ParsingContext<?> ctx = createParsingContext(cls, input);
		return create(cls, ctx);
	}

	/**
	 * Create a parsing context for the given parserClass and input. The
	 * instantiated type is based on the generic type information of the parser
	 * class. The context has to have a constructor with a single {@link String}
	 * parameter.
	 */
	public static ParsingContext<?> createParsingContext(Class<?> parserClass,
			String input) {
		ParsingContext<?> ctx;
		try {
			ctx = (ParsingContext<?>) getParsingContextType(parserClass)
					.getConstructor(String.class).newInstance(input);
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(
					"Error while instantiating parsing context", e);
		}
		return ctx;
	}

	/**
	 * Instantiate a weaved instance of a parser class and return a proxy
	 * delegating to the instantiated parser.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Parser<?>> T create(Class<T> cls,
			ParsingContext<?> ctx) {
		String parserClassNasme = cls.getName();
		Object weavedParser = instantiateWeavedParser(ctx, parserClassNasme);

		Enhancer e = new Enhancer();
		e.setSuperclass(cls);

		e.setCallback(new MethodInterceptor() {

			@Override
			public Object intercept(Object obj, Method method, Object[] args,
					MethodProxy proxy) throws Throwable {
				Method m = findMethod(weavedParser.getClass().getMethods(),
						method.getName(), method.getParameterTypes());
				if (m == null) {
					m = findMethod(weavedParser.getClass(), method.getName(),
							method.getParameterTypes());
				}
				m.setAccessible(true);
				try {
					return m.invoke(weavedParser, args);
				} catch (InvocationTargetException e) {
					throw e.getCause();
				}
			}

			private Method findMethod(Class<?> cls, String methodName,
					Class<?>[] parameterTypes) throws NoSuchMethodException,
					SecurityException {
				if (cls == null)
					return null;
				Method m = findMethod(cls.getDeclaredMethods(), methodName,
						parameterTypes);
				return m == null ? findMethod(cls.getSuperclass(), methodName,
						parameterTypes) : m;
			}

			private Method findMethod(Method[] candidates, String methodName,
					Class<?>[] parameterTypes) {
				for (Method m : candidates) {
					if (methodName.equals(m.getName())
							&& Arrays.equals(parameterTypes,
									m.getParameterTypes()))
						return m;
				}
				return null;
			}
		});

		return (T) e.create(new Class[] { getParsingContextType(cls) },
				new Object[] { ctx });

	}

	private static Object instantiateWeavedParser(ParsingContext<?> ctx,
			String parserClassNasme) {

		Class<?> weavedClass;
		try {
			weavedClass = new WeavedClassLoader(
					ParserFactory.class.getClassLoader(), parserClassNasme,
					weaveClass(parserClassNasme)).loadClass(parserClassNasme);

			Constructor<?> constructor = weavedClass
					.getConstructor(getParsingContextType(weavedClass));
			constructor.setAccessible(true);
			Object weavedParser = constructor.newInstance(ctx);
			return weavedParser;
		} catch (ClassNotFoundException | NoSuchMethodException
				| SecurityException | InstantiationException
				| IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException(
					"Error while weaving and instantiating parser class", e);
		}
	}

	private static Class<?> getParsingContextType(Class<?> parserClass) {
		return TypeToken.of(parserClass)
				.resolveType(Parser.class.getTypeParameters()[0]).getRawType();
	}

	@SuppressWarnings("unchecked")
	private static byte[] weaveClass(String parserClassName) {
		InputStream in = ParserFactory.class.getClassLoader()
				.getResourceAsStream(
						parserClassName.replace('.', '/') + ".class");
		ClassReader classReader;
		try {
			classReader = new ClassReader(in);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// load class
		ClassNode cn = new ClassNode();
		classReader.accept(cn, ClassReader.EXPAND_FRAMES);

		// load prototype method
		MethodNode prototype = loadPrototypeMethodNode();

		// modify methods
		for (int i = 0; i < cn.methods.size(); i++) {
			MethodNode ruleNode = (MethodNode) cn.methods.get(i);
			if ("<init>".equals(ruleNode.name))
				continue;
			if ((Opcodes.ACC_STATIC & ruleNode.access) != 0)
				continue;
			if ((Opcodes.ACC_SYNTHETIC & ruleNode.access) != 0)
				continue;

			// get minimum and maximum line numbers
			MinMaxLineMethodAdapter minMaxLineMethodAdapter = new MinMaxLineMethodAdapter(
					Opcodes.ASM5, null);
			ruleNode.accept(minMaxLineMethodAdapter);

			// prepare the new method node
			MethodNode newNode;
			{
				String[] exceptions = ((List<String>) ruleNode.exceptions)
						.toArray(new String[] {});

				newNode = new MethodNode(ruleNode.access, ruleNode.name,
						ruleNode.desc, ruleNode.signature, exceptions);
			}

			// replace remaining references to PrototypeParser
			RemappingMethodAdapter remapper = new RemappingMethodAdapter(
					ruleNode.access, ruleNode.desc, newNode,
					new SimpleRemapper(PrototypeParser.class.getName().replace(
							'.', '/'), parserClassName.replace('.', '/')));

			// inline the call to the sampleRule method, replace it with the
			// original rule method
			MethodCallInliner inliner = new MethodCallInliner(remapper,
					ruleNode, minMaxLineMethodAdapter);

			// customize the code found in the prototype
			PrototypeCustomizer prototypeCustomizer = new PrototypeCustomizer(
					inliner, ruleNode, i);

			// shift local variables to make space for parameters of the rule
			// method
			LocalVariableShifter shifter = new LocalVariableShifter(
					Type.getArgumentTypes(ruleNode.desc).length,
					prototype.access, prototype.desc, prototypeCustomizer);

			// trigger the transformation
			prototype.instructions.resetLabels();
			prototype.accept(shifter);

			// replace the existing method
			cn.methods.set(i, newNode);
		}

		// dump weaved byte code
		// cn.accept(new TraceClassVisitor(null, new Textifier(), new
		// PrintWriter(
		// System.out)));

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
				+ ClassWriter.COMPUTE_FRAMES);
		cn.accept(cw);

		byte[] b = cw.toByteArray();

		// verify weaved byte code
		PrintWriter pw = new PrintWriter(System.out);
		CheckClassAdapter.verify(new ClassReader(b), false, pw);

		return b;
	}

	private static MethodNode loadPrototypeMethodNode() {
		InputStream in = ParserFactory.class.getClassLoader()
				.getResourceAsStream(
						PrototypeParser.class.getName().replace('.', '/')
								+ ".class");
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
