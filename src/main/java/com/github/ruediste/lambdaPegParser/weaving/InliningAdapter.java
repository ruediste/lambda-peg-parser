package com.github.ruediste.lambdaPegParser.weaving;

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
    private final Label end;
    private LocalVariablesSorter lvs;

    public InliningAdapter(LocalVariablesSorter mv, int access, String desc,
            Label end) {
        super(Opcodes.ASM5, access, desc, mv);
        this.end = end;
        this.lvs = mv;
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
        // swallow
    }

    @Override
    protected int newLocalMapping(Type type) {
        return lvs.newLocal(type);
    }
}