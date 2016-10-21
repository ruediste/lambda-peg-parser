package com.github.ruediste.lambdaPegParser.weaving;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * Shift the local variable indexes by a certain offset
 */
public class LocalVariableShifter extends LocalVariablesSorter {

    private int offset;

    public LocalVariableShifter(int offset, int access, String desc, MethodVisitor mv) {
        super(Opcodes.ASM5, access, desc, mv);
        this.offset = offset;
    }

    @Override
    protected int newLocalMapping(Type type) {
        return super.newLocalMapping(type) + offset;
    }
}
