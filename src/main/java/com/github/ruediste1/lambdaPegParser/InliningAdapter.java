package com.github.ruediste1.lambdaPegParser;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * Adapter for to be inlined code.
 * 
 * This adapter does all parameter renaming and replacing of the RETURN opcodes
 * 
 * 
 */
public class InliningAdapter extends LocalVariablesSorter {
	private final LocalVariablesSorter lvs;
	private final Label end;

	public InliningAdapter(LocalVariablesSorter mv, Label end, int acc,
			String desc) {
		super(Opcodes.ASM5, acc, desc, mv);
		this.lvs = mv;
		this.end = end;
		int off = (acc & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		Type[] args = Type.getArgumentTypes(desc);
		for (int i = args.length - 1; i >= 0; i--) {
			super.visitVarInsn(args[i].getOpcode(Opcodes.ISTORE), i + off);
		}
		if (off > 0) {
			super.visitVarInsn(Opcodes.ASTORE, 0);
		}
	}

	@Override
	public void visitInsn(int opcode) {
		if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
			super.visitJumpInsn(Opcodes.GOTO, end);
		} else {
			super.visitInsn(opcode);
		}
	}

	@Override
	public void visitMaxs(int stack, int locals) {
	}

	@Override
	protected int newLocalMapping(Type type) {
		return lvs.newLocal(type);
	}
}