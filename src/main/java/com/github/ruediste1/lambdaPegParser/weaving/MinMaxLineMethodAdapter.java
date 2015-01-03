package com.github.ruediste1.lambdaPegParser.weaving;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * Method Visitor determining the minimal and maximal line number in a method
 */
public class MinMaxLineMethodAdapter extends MethodVisitor {

	private Integer minLineNumber;
	private Integer maxLineNumber;

	public MinMaxLineMethodAdapter(int api, MethodVisitor mv) {
		super(api, mv);
	}

	public Integer getMaxLineNumber() {
		return maxLineNumber;
	}

	public int getMaxLineNumberOr(int fallback) {
		return maxLineNumber == null ? fallback : maxLineNumber;
	}

	public Integer getMinLineNumber() {
		return minLineNumber;
	}

	public int getMinLineNumberOr(int fallback) {
		return minLineNumber == null ? fallback : minLineNumber;
	}

	@Override
	public void visitLineNumber(int line, Label start) {
		if (minLineNumber == null || minLineNumber > line)
			minLineNumber = line;
		if (maxLineNumber == null || maxLineNumber < line)
			maxLineNumber = line;
		super.visitLineNumber(line, start);
	}
}
