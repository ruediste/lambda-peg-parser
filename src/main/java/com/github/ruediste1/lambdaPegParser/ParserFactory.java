package com.github.ruediste1.lambdaPegParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.RemappingMethodAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class ParserFactory {

	public static class WeavedClassLoader extends ClassLoader {

		private Class<?> cls;

		public WeavedClassLoader(ClassLoader parent, Class<?> cls) {
			super(parent);
			this.cls = cls;
		}

		public MethodNode loadPrototypeMethodNode() {
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

		@SuppressWarnings("unchecked")
		@Override
		public Class<?> loadClass(String parserClassName)
				throws ClassNotFoundException {

			if (cls.getName().equals(parserClassName)) {
				InputStream in = ParserFactory.class.getClassLoader()
						.getResourceAsStream(
								cls.getName().replace('.', '/') + ".class");
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

					MethodNode newNode;
					{
						String[] exceptions = ((List<String>) ruleNode.exceptions)
								.toArray(new String[] {});

						newNode = new MethodNode(ruleNode.access,
								ruleNode.name, ruleNode.desc,
								ruleNode.signature, exceptions);
					}

					RemappingMethodAdapter remapper = new RemappingMethodAdapter(
							ruleNode.access, ruleNode.desc, newNode,
							new SimpleRemapper(PrototypeParser.class.getName()
									.replace('.', '/'),
									parserClassName.replace('.', '/')));

					MethodCallInliner inliner = new MethodCallInliner(remapper,
							ruleNode, minMaxLineMethodAdapter);

					PrototypeCustomizer prototypeCustomizer = new PrototypeCustomizer(
							inliner, ruleNode, i);

					prototype.accept(prototypeCustomizer);

					cn.methods.set(i, newNode);
				}

				cn.accept(new TraceClassVisitor(null, new Textifier(),
						new PrintWriter(System.out)));

				// load modified class
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
						+ ClassWriter.COMPUTE_FRAMES);
				cn.accept(cw);

				byte[] b = cw.toByteArray();

				PrintWriter pw = new PrintWriter(System.out);
				CheckClassAdapter.verify(new ClassReader(b), false, pw);

				defineClass(parserClassName, b, 0, b.length);
			}
			return super.loadClass(parserClassName);
		}
	}

	public static <T extends Parser> T create(Class<T> cls, String input) {
		ParsingContext ctx = new ParsingContext(input);
		return create(cls, ctx);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Parser> T create(Class<T> cls, ParsingContext ctx) {
		try {
			Class<?> weavedClass = new WeavedClassLoader(
					ParserFactory.class.getClassLoader(), cls).loadClass(cls
					.getName());

			Constructor<?> constructor = weavedClass
					.getConstructor(ParsingContext.class);
			constructor.setAccessible(true);
			Object weavedParser = constructor.newInstance(ctx);

			Enhancer e = new Enhancer();
			e.setSuperclass(cls);

			e.setCallback(new MethodInterceptor() {

				@Override
				public Object intercept(Object obj, Method method,
						Object[] args, MethodProxy proxy) throws Throwable {
					Method m = weavedClass.getDeclaredMethod(method.getName(),
							method.getParameterTypes());
					m.setAccessible(true);
					return m.invoke(weavedParser, args);
				}
			});

			return (T) e.create(new Class[] { ParsingContext.class },
					new Object[] { ctx });
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			throw new RuntimeException(
					"Error while weaving and instantiating parser class", e);
		}

	}

}
