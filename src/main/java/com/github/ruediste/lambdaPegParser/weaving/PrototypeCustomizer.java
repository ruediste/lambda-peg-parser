package com.github.ruediste.lambdaPegParser.weaving;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.MethodNode;

import com.github.ruediste.lambdaPegParser.PrototypeParser;

/**
 * Replaces some method invocations present in the prototype parser
 */
public class PrototypeCustomizer extends GeneratorAdapter {

    private int ruleMethodNr;
    private MethodNode ruleNode;
    private MethodVisitor origMv;
    private boolean memo;

    public PrototypeCustomizer(MethodVisitor mv, MethodNode ruleNode, int ruleMethodNr, boolean memo) {
        super(Opcodes.ASM5, mv, ruleNode.access, ruleNode.name, ruleNode.desc);
        origMv = mv;
        this.ruleNode = ruleNode;
        this.ruleMethodNr = ruleMethodNr;
        this.memo = memo;
    }

    private MethodVisitor sinkMv = new MethodVisitor(Opcodes.ASM5) {
    };

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {

        if (Type.getInternalName(PrototypeParser.class).equals(owner)) {
            if ("getMethodNumber".equals(name)) {
                push(ruleMethodNr);
            } else if ("getMethodName".equals(name)) {
                if (mv != null)
                    mv.visitLdcInsn(ruleNode.name);
            } else if ("getArgs".equals(name)) {
                loadArgArray();
            } else if ("getArgumentTypes".equals(name)) {
                Type[] argumentTypes = Type.getArgumentTypes(desc);
                push(argumentTypes.length);
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                for (int i = 0; i < argumentTypes.length; i++) {
                    dup();
                    push(i);
                    push(argumentTypes[i]);
                    mv.visitInsn(Opcodes.AASTORE);
                }
            } else if ("startMemo".equals(name)) {
                if (!memo)
                    mv = sinkMv;
            } else if ("stopMemo".equals(name)) {
                if (!memo)
                    mv = origMv;
            } else
                super.visitMethodInsn(opcode, owner, name, desc, itf);
        } else
            super.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.ARETURN) {
            // unbox return value if necessary
            Type returnType = Type.getMethodType(ruleNode.desc).getReturnType();
            unbox(returnType);
            returnValue();
        } else
            super.visitInsn(opcode);
    }
}
