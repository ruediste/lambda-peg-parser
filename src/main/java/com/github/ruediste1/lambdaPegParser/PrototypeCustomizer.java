package com.github.ruediste1.lambdaPegParser;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.MethodNode;

public class PrototypeCustomizer extends GeneratorAdapter {

	private int ruleMethodNr;
	private MethodNode ruleNode;

	public PrototypeCustomizer(MethodVisitor mv, MethodNode ruleNode,
			int ruleMethodNr) {
		super(Opcodes.ASM5, mv, ruleNode.access, ruleNode.name, ruleNode.desc);
		this.ruleNode = ruleNode;
		this.ruleMethodNr = ruleMethodNr;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if ("com/github/ruediste1/lambdaPegParser/PrototypeParser"
				.equals(owner)) {
			if ("getMethodNumber".equals(name)) {
				push(ruleMethodNr);
			} else if ("getMethodName".equals(name)) {
				mv.visitLdcInsn(ruleNode.name);
			} else if ("getArgs".equals(name)) {
				loadArgArray();
			} else
				super.visitMethodInsn(opcode, owner, name, desc, itf);
		} else
			super.visitMethodInsn(opcode, owner, name, desc, itf);
	}

	@Override
	public void visitInsn(int opcode) {
		if (opcode == Opcodes.ARETURN) {
			Type returnType = Type.getMethodType(ruleNode.desc).getReturnType();
			unbox(returnType);
			returnValue();
		} else
			super.visitInsn(opcode);
	}
}
