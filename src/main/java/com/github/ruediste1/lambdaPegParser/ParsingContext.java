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
 */
public class ParsingContext<TState extends ParsingState<TState>> {
	private String content;

	private TState state;

	public ParsingContext(String content) {
		setContent(content);
	}

	public final Event<String> contentSetEvent = new Event<>();

	public final void setContent(String content) {
		this.content = content;
		state = createInitialState();
		expectationFrame = new ExpectationFrame();
		contentSetEvent.fire(content);
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

	/**
	 * Return true if there are more codepoints in the input
	 */
	public boolean hasNext() {
		return getIndex() < content.length();
	}

	/**
	 * Return the current input position
	 */
	public int getIndex() {
		return state.getIndex();
	}

	/**
	 * State Snapshots allow to capure a state via
	 * {@link ParsingContext#snapshot()} and restore them later ( using
	 * {@link #restore()} {@link #restoreClone()} )
	 */
	public interface StateSnapshot {

		/**
		 * Restore the snapshot. May be used once only
		 */
		void restore();

		/**
		 * restore a clone of the snapshot.
		 */
		void restoreClone();
	}

	/**
	 * Implementation of {@link StateSnapshot}
	 */
	private class StateSnapshotImpl implements StateSnapshot {
		TState snapshot;

		public StateSnapshotImpl() {
			snapshot = state.clone();
		}

		@Override
		public void restore() {
			checkSnapshot();
			state = snapshot;
			snapshot = null;
		}

		@Override
		public void restoreClone() {
			checkSnapshot();
			state = snapshot.clone();
		}

		private void checkSnapshot() {
			if (snapshot == null)
				throw new RuntimeException(
						"cannot restore after the first call to restore()");
		}

	}

	/**
	 * Create a snapshot of the current state
	 */
	public StateSnapshot snapshot() {
		return new StateSnapshotImpl();
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

	public static class Expectation {
		public int index;
		public String expectation;

		public Expectation(int index, String expectation) {
			super();
			this.index = index;
			this.expectation = expectation;
		}

	}

	public final Event<Expectation> expectationRegistered = new Event<>();

	/**
	 * Register an expectation at the supplied index with the current
	 * {@link ExpectationFrame}
	 */
	public void registerExpectation(int index, String expectation) {
		expectationFrame.registerExpectation(index, expectation);
		expectationRegistered.fire(new Expectation(index, expectation));
	}

	/**
	 * Return an error description according to the currently reported
	 * expectations
	 */
	public ErrorDesciption getErrorDescription() {
		ErrorDesciption result = new ErrorDesciption(
				Collections.unmodifiableSet(expectationFrame.expectations),
				content, expectationFrame.index);
		return result;
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

		public LineInfo errorLineInfo;

		public ErrorDesciption(Set<String> expectations, String content,
				int errorPosition) {
			this.expectations = expectations;
			this.errorPosition = errorPosition;
			errorLineInfo = new LineInfo(content, errorPosition);
		}

		@Override
		public String toString() {
			return "Error on line " + errorLineInfo.getLineNr()
					+ ". Expected: "
					+ expectations.stream().collect(joining(", ")) + "\n"
					+ errorLineInfo.getLine() + "\n"
					+ errorLineInfo.getErrorLineUnderline(' ', '^');
		}

	}

	public final Event<RuleLoggingInfo> recursiveEvent = new Event<>();

	public void recursive(RuleLoggingInfo loggingInfo) {
		loggingInfo.index = getIndex();
		recursiveEvent.fire(loggingInfo);
	}

	public final Event<RuleLoggingInfo> enteringEvent = new Event<>();

	public void entering(RuleLoggingInfo loggingInfo) {
		loggingInfo.index = getIndex();
		enteringEvent.fire(loggingInfo);
	}

	public final Event<RuleLoggingInfo> failedEvent = new Event<>();

	public void failed(RuleLoggingInfo loggingInfo) {
		loggingInfo.index = getIndex();
		failedEvent.fire(loggingInfo);
	}

	public final Event<RuleLoggingInfo> leavingEvent = new Event<>();

	public void leaving(RuleLoggingInfo loggingInfo) {
		loggingInfo.index = getIndex();
		leavingEvent.fire(loggingInfo);
	}

	public final Event<RuleLoggingInfo> retryingEvent = new Event<>();

	public void retrying(RuleLoggingInfo loggingInfo) {
		loggingInfo.index = getIndex();
		retryingEvent.fire(loggingInfo);
	}

	@Override
	public String toString() {
		LineInfo info = new LineInfo(content, getIndex());
		return getClass().getSimpleName() + "Line " + info.getLineNr() + "\n"
				+ info.getLine() + "\n" + info.getErrorLineUnderline(' ', '*');
	}
}
