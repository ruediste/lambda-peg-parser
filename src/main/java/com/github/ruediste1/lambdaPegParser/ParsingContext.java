package com.github.ruediste1.lambdaPegParser;

import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Context of a parsing run.
 * 
 * <p>
 * {@link Parser}s have a reference to a {@link ParsingContext}. The context
 * contains the input. If multiple parsers reference the same context instance,
 * they can collaborate. This enables pluggalbe grammars.
 * </p>
 * 
 * <p>
 * The parsing state is encapsulated within a {@link ParsingState}. The current
 * state can be saved using {@link #snapshot()} and restored using
 * {@link StateSnapshot#restore()}. This allows backtracking.
 * </p>
 * 
 * <p>
 * 
 * </p>
 */
public class ParsingContext<TState extends ParsingState<TState>> {
	private String content;

	private TState state;

	public ParsingContext(String content) {
		setContent(content);
	}

	public final void setContent(String content) {
		this.content = content;
		state = createInitialState();
		expectationFrame = new ExpectationFrame();
	}

	@SuppressWarnings("unchecked")
	protected TState createInitialState() {
		return (TState) new ParsingState<>();
	}

	/**
	 * Return the next codepoint of the input without consuming it
	 */
	public int peek() {
		if (!hasNext())
			throw new NoMatchException();
		return content.codePointAt(getIndex());
	}

	/**
	 * Return the next codepoint of the input and consume it.
	 */
	public int next() {
		if (!hasNext()) {
			throw new NoMatchException();
		}
		int result = content.codePointAt(getIndex());
		state.setIndex(getIndex() + Character.charCount(result));
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

		void restoreClone();
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

		@Override
		public void restoreClone() {
			state = snapshot.clone();
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

	public void recursive(Class<?> cls, java.lang.String methodName) {
		System.out.println(indent() + cls.getName() + "." + methodName
				+ " recursive, advancing to: " + getIndex());
	}

	/**
	 * Collects expectations. Used for error reporting
	 */
	public static class ExpectationFrame {
		public int index;
		public Set<String> expectations = new LinkedHashSet<>();

		/**
		 * Register an expectation. If the supplied index lies farther to the
		 * right than {@link #index}, the expectations are cleared and the index
		 * is advanced. The expectation is only added to the expectation if the
		 * supplied index does NOT lie to the left of {@link #index}.
		 */
		public void registerExpectation(int index, String expectation) {
			if (this.index < index) {
				this.index = index;
				expectations.clear();
			}
			if (this.index == index)
				expectations.add(expectation);
		}

		/**
		 * Merge two frames. The one farther to the right takes precedence. If
		 * both are at the same position, the expectations are merged.
		 */
		public void merge(ExpectationFrame other) {
			if (index == other.index) {
				expectations.addAll(other.expectations);
			}
			if (index < other.index) {
				expectations = new HashSet<>(other.expectations);
			}
		}
	}

	private ExpectationFrame expectationFrame;

	/**
	 * Register an expectation at the current index with the current
	 * {@link ExpectationFrame}
	 */
	public void registerExpectation(String expectation) {
		registerExpectation(getIndex(), expectation);
	}

	/**
	 * Register an expectation at the supplied index with the current
	 * {@link ExpectationFrame}
	 */
	public void registerExpectation(int index, String expectation) {
		expectationFrame.registerExpectation(index, expectation);
	}

	public String getErrorMessage() {
		return getErrorDescription().toString();
	}

	public ErrorDesciption getErrorDescription() {
		ErrorDesciption result = new ErrorDesciption();
		result.errorPosition = expectationFrame.index;
		result.expectations = Collections
				.unmodifiableSet(expectationFrame.expectations);

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

	public ExpectationFrame getExpectationFrame() {
		return expectationFrame;
	}

	public void setExpectationFrame(ExpectationFrame expectationFrame) {
		this.expectationFrame = expectationFrame;
	}

	public ExpectationFrame setNewExpectationFrame() {
		this.expectationFrame = new ExpectationFrame();
		return expectationFrame;
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
