package com.github.ruediste1.lambdaPegParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class WeavingClassVisitor extends ClassVisitor {

	private MethodNode prototypeNode;

	public WeavingClassVisitor(ClassVisitor cv) {
		super(Opcodes.ASM5, cv);
		InputStream in = ParserFactory.class
				.getResourceAsStream(PrototypeParser.class.getName().replace(
						'.', '/')
						+ ".class");
		ClassReader classReader;
		try {
			classReader = new ClassReader(in);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		ClassNode classNode = new ClassNode();
		classReader.accept(classNode, 0);
		Iterator<?> it = classNode.methods.iterator();
		while (it.hasNext()) {
			MethodNode node = (MethodNode) it.next();
			if ("prototypeAdvice".equals(node.name)) {
				prototypeNode = node;
			}
		}
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature,
				exceptions);

		return new MethodCallInliner(mv, prototypeNode);
	};
}
