package com.github.ruediste1.lambdaPegParser.weaving;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.MethodNode;

public class MethodCallInliner extends GeneratorAdapter {
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
    private boolean afterInlining;
    private MinMaxLineMethodAdapter minMaxLineMethodAdapter;

    public MethodCallInliner(MethodVisitor mv, MethodNode toBeInlined,
            MinMaxLineMethodAdapter minMaxLineMethodAdapter) {
        super(Opcodes.ASM5, mv, toBeInlined.access, toBeInlined.name,
                toBeInlined.desc);
        this.toBeInlined = toBeInlined;
        this.minMaxLineMethodAdapter = minMaxLineMethodAdapter;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        if (!inlining) {
            if (!afterInlining) {
                int min = minMaxLineMethodAdapter.getMinLineNumberOr(1);
                super.visitLineNumber(min > 1 ? min - 1 : 1, start);
            } else {
                int max = minMaxLineMethodAdapter.getMaxLineNumberOr(1);
                super.visitLineNumber(max + 1, start);
            }
        } else
            super.visitLineNumber(line, start);
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
        toBeInlined.accept(new InliningAdapter(this, toBeInlined.access,
                toBeInlined.desc, end));
        inlining = false;
        afterInlining = true;

        // visit the end label
        super.visitLabel(end);

        // box the return value if necessary
        Type returnType = Type.getMethodType(toBeInlined.desc).getReturnType();
        valueOf(returnType);

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

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack,
            Object[] stack) {
        // swallow
    }
}