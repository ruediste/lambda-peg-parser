package com.github.ruediste1.lambdaPegParser;

import static java.util.stream.Collectors.joining;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class ParsingContext {
	String content;

	private int index;

	public ParsingContext(String content) {
		this.content = content;
		expectationFrameStack.push(new ExpectationFrame());
	}

	public int next() {
		if (isEOI()) {
			throw new NoMatchException(this, "any character");
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

	public void entering(Class<?> cls, String methodName) {
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Entering, index: " + index);
		depth++;
	}

	public void failed(Class<?> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Failed, index: " + index);
	}

	public void leaving(Class<?> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Leaving, index: " + index);
	}

	public void retrying(Class<?> cls, String methodName) {
		System.out.println(indent(depth - 1) + cls.getName() + "." + methodName
				+ " Retrying, was at index: " + index);
	}

	private static class ExpectationFrame {
		int index;
		Set<String> expectations = new HashSet<>();

		public void addExpectation(int index, String expectation) {
			if (this.index < index) {
				this.index = index;
				expectations.clear();
			}
			expectations.add(expectation);
		}
	}

	private Deque<ExpectationFrame> expectationFrameStack = new ArrayDeque<>();

	public void registerExpectation(String expectation) {
		expectationFrameStack.peek().addExpectation(index, expectation);
	}

	public void pushExpectationFrame() {
		expectationFrameStack.push(new ExpectationFrame());
	}

	public void popExpectationFrame() {
		expectationFrameStack.pop();
	}

	/**
	 * Pop the current {@link ExpectationFrame} and add the new expectation at
	 * the index of the current frame to the underlying frame.
	 */
	public void popExpectationFrame(String newExpectation) {
		ExpectationFrame frame = expectationFrameStack.pop();
		expectationFrameStack.peek()
				.addExpectation(frame.index, newExpectation);
	}

	public String getErrorMessage() {
		return getErrorDescription().toString();
	}

	public Set<String> getExpectations() {
		return Collections
				.unmodifiableSet(expectationFrameStack.peek().expectations);
	}

	public ErrorDesciption getErrorDescription() {
		ErrorDesciption result = new ErrorDesciption();
		result.errorPosition = expectationFrameStack.peek().index;
		result.expectations = getExpectations();

		fillLineInfo(result, content);
		return result;
	}

	static void fillLineInfo(ErrorDesciption result, String content) {
		int lineNr = 1;
		int idx = 0;
		while (true) {
			int endIdx = content.indexOf('\n', idx);

			if (result.errorPosition >= idx
					&& (endIdx == -1 || endIdx + 1 > result.errorPosition)) {
				result.errorLine = content.substring(idx,
						endIdx == -1 ? content.length() : endIdx);
				result.indexInErrorLine = result.errorPosition - idx;
				result.errorLineNr = lineNr;
				break;
			}
			if (endIdx == -1)
				break;
			idx = endIdx + 1;
			lineNr++;
		}
	}

	public static class ErrorDesciption {
		public int errorPosition;
		public Set<String> expectations;

		/**
		 * Line number the error occured in. First line has count 1
		 */
		public int errorLineNr;

		/**
		 * Input line the error occured in
		 */
		public String errorLine;

		/**
		 * index of the error in the error line
		 */
		public int indexInErrorLine;

		/**
		 * Return a line suitable to underline the error line
		 * 
		 * @param spacerCP
		 *            codePoint of the caracter to use as space
		 * @param positionMarkerCP
		 *            codePoint of the caracter to use as marker
		 */
		public String getErrorLineUnderline(int spacerCP, int positionMarkerCP) {
			StringBuilder sb = new StringBuilder();
			int i = 0;
			for (; i < indexInErrorLine; i++) {
				sb.appendCodePoint(spacerCP);
			}

			sb.appendCodePoint(positionMarkerCP);
			for (; i < errorLine.length() - 1; i++) {
				sb.appendCodePoint(spacerCP);
			}
			return sb.toString();
		}

		@Override
		public String toString() {
			return "Error on line " + errorLineNr + ". Expected: "
					+ expectations.stream().collect(joining(", ")) + "\n"
					+ errorLine + "\n" + getErrorLineUnderline(' ', '^');
		}
	}
}
