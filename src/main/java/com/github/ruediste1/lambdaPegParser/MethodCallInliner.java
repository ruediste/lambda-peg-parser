package com.github.ruediste1.lambdaPegParser;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.tree.MethodNode;

public class MethodCallInliner extends LocalVariablesSorter {
	public class CatchBlock {

		private Label start;
		private Label handler;
		private String type;
		private Label end;

		public CatchBlock(Label start, Label end, Label handler, String type) {
			this.start = start;
			this.end = end;
			this.handler = handler;
			this.type = type;
		}

	}

	private final MethodNode toBeInlined;
	private List<CatchBlock> blocks = new ArrayList<CatchBlock>();
	private boolean inlining;

	public MethodCallInliner(int access, String desc, MethodVisitor mv,
			MethodNode toBeInlined) {
		super(Opcodes.ASM5, access, desc, mv);
		this.toBeInlined = toBeInlined;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if (!shouldBeInlined(owner, name, desc)) {
			mv.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}

		Label end = new Label();
		inlining = true;
		toBeInlined.instructions.resetLabels();

		// pass the to be inlined method through the inlining adapter to this
		toBeInlined.accept(new InliningAdapter(this, end,
				opcode == Opcodes.INVOKESTATIC ? Opcodes.ACC_STATIC : 0, desc));
		inlining = false;
		super.visitLabel(end);
	}

	private boolean shouldBeInlined(String owner, String name, String desc) {
		return "com/github/ruediste1/lambdaPegParser/PrototypeParser"
				.equals(owner) && "sampleRule".equals(name);
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler,
			String type) {
		if (!inlining) {
			blocks.add(new CatchBlock(start, end, handler, type));
		} else {
			super.visitTryCatchBlock(start, end, handler, type);
		}
	}

	@Override
	public void visitMaxs(int stack, int locals) {
		for (CatchBlock b : blocks)
			super.visitTryCatchBlock(b.start, b.end, b.handler, b.type);
		super.visitMaxs(stack, locals);
	}
}