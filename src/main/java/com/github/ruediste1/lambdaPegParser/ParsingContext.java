package com.github.ruediste1.lambdaPegParser;

import static java.util.stream.Collectors.joining;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class ParsingContext<TState extends ParsingState<TState>> {
	private String content;

	private TState state;

	public ParsingContext(String content) {
		this.content = content;
		state = createInitialState();
		expectationFrameStack.push(new ExpectationFrame());
	}

	public void setContent(String content) {
		this.content = content;
		state = createInitialState();
		expectationFrameStack.clear();
		expectationFrameStack.push(new ExpectationFrame());
	}

	@SuppressWarnings("unchecked")
	protected TState createInitialState() {
		return (TState) new ParsingState<>();
	}

	public int next() {
		if (isEOI()) {
			throw new NoMatchException(this, "any character");
		}
		int result = content.codePointAt(getIndex());
		setIndex(getIndex() + Character.charCount(result));
		return result;
	}

	public boolean hasNext() {
		return getIndex() < content.length();
	}

	public boolean isEOI() {
		return !hasNext();
	}

	public int getIndex() {
		return state.getIndex();
	}

	public void setIndex(int index) {
		state.setIndex(index);
	}

	public interface StateSnapshot {
		void restore();
	}

	private class StateSnapshotImpl implements StateSnapshot {
		TState snapshot;

		public StateSnapshotImpl() {
			snapshot = state.clone();
		}

		@Override
		public void restore() {
			state = snapshot;
		}

	}

	public StateSnapshot snapshot() {
		return new StateSnapshotImpl();
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
				+ " Entering, index: " + getIndex());
		depth++;
	}

	public void failed(Class<?> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Failed, index: " + getIndex());
	}

	public void leaving(Class<?> cls, String methodName) {
		depth--;
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " Leaving, index: " + getIndex());
	}

	public void retrying(Class<?> cls, String methodName) {
		System.out.println(indent(depth - 1) + cls.getName() + "." + methodName
				+ " Retrying, was at index: " + getIndex());
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
		registerExpectation(getIndex(), expectation);
	}

	public void registerExpectation(int index, String expectation) {
		expectationFrameStack.peek().addExpectation(index, expectation);
	}

	public void pushExpectationFrame() {
		expectationFrameStack.push(new ExpectationFrame());
	}

	public void popExpectationFrame(int index, String replacementExpectation) {
		ExpectationFrame frame = expectationFrameStack.pop();
		if (frame.index > getExpectationsIndex()) {
			// the frame to pop is farther to the right, use it's expectations
			expectationFrameStack.peek().expectations = frame.expectations;
		} else if (frame.index == getExpectationsIndex()) {
			// the frame is at the same position as the underlying frame
			if (index == frame.index)
				// replace all expectations of frame with the
				// replacementExpectation
				expectationFrameStack.peek().expectations
						.add(replacementExpectation);
			else
				// merge frames
				expectationFrameStack.peek().expectations
						.addAll(frame.expectations);

		}
	}

	public String getErrorMessage() {
		return getErrorDescription().toString();
	}

	public int getExpectationsIndex() {
		return expectationFrameStack.peek().index;
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
