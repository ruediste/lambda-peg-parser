package com.github.ruediste1.lambdaPegParser;

import java.lang.reflect.Method;

public class ParsingContext {
	String content;

	private int index;

	public ParsingContext(String content) {
		this.content = content;
	}

	public int next() {
		if (isEOI()) {
			throw new NoMatchException();
		}
		int result = content.codePointAt(getIndex());
		index = index + Character.charCount(result);
		return result;
	}

	public boolean isEOI() {
		return getIndex() >= content.length();
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	private int depth;

	private String indent() {
		return indent(depth);
	}

	private String indent(int depth) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
		return sb.toString();
	}

	public void entering(Method method) {
		System.out.println(indent() + method + " Entering, index: " + index);
		depth++;
	}

	public void leaving(Method method) {
		depth--;
		System.out.println(indent() + method + " Leaving, index: " + index);
	}

	public void failed(Method method) {
		depth--;
		System.out.println(indent() + method + " Failed, index: " + index);
	}

	public void retrying(Method method) {
		System.out.println(indent(depth - 1) + method
				+ " Retrying, was at index: " + index);
	}

	public void entering(Class<? extends PrototypeParser> cls, String methodName) {
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Entering, index: " + index);
		depth++;
	}

	public void failed(Class<? extends PrototypeParser> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Failed, index: " + index);
	}

	public void leaving(Class<? extends PrototypeParser> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Leaving, index: " + index);
	}

	public void retrying(Class<? extends PrototypeParser> cls, String methodName) {
		System.out.println(indent(depth - 1) + cls.getName() + "." + methodName
				+ " Retrying, was at index: " + index);
	}

}
