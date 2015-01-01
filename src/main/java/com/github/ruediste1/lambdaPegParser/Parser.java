package com.github.ruediste1.lambdaPegParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Base class for parser classes.
 */
public class Parser {

	private final ParsingContext ctx;

	protected HashMap<RuleInvocation, RuleInvocation> currentMethods = new HashMap<>();

	public Parser(ParsingContext ctx) {
		this.ctx = ctx;
	}

	/**
	 * Represents a seed for handling left recursive grammars.
	 */
	protected static class Seed {
		public Object value;
		public int index;

		public Seed(Object value, int index) {
			super();
			this.value = value;
			this.index = index;
		}

	}

	/**
	 * The invocation of a rule. Contains the method and arguments as well as
	 * the input position. Used to handle left recursive grammars.
	 */
	protected static class RuleInvocation {
		public int method;
		public Object[] args;
		public int position;

		public boolean recursive;
		public Seed seed;

		@Override
		public String toString() {
			return "RuleInvocation [method=" + method + ", args="
					+ Arrays.toString(args) + ", position=" + position
					+ ", recursive=" + recursive + ", seed=" + seed + "]";
		}

		@Override
		public int hashCode() {
			return Objects.hash(method, Arrays.hashCode(args), position);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			RuleInvocation other = (RuleInvocation) obj;
			return Objects.equals(method, other.method)
					&& Arrays.equals(args, other.args)
					&& Objects.equals(position, other.position);
		}

		public RuleInvocation(int method, Object[] args, int position) {
			super();
			this.method = method;
			this.args = args;
			this.position = position;
		}
	}

	/**
	 * Matches the end of the input
	 */
	public final void EOI() {
		if (!getParsingContext().isEOI()) {
			throw new NoMatchException(getParsingContext(), "End Of Input");
		}
	}

	/**
	 * Tries each choice in turn until a choice can successfully be matched
	 */
	@SafeVarargs
	public final void FirstOf(Runnable... choices) {
		for (Runnable choice : choices) {
			int index = getParsingContext().getIndex();
			try {
				choice.run();
				return;
			} catch (NoMatchException e) {
				// swallow, restore index
				getParsingContext().setIndex(index);
			}
		}
		throw new NoMatchException();
	}

	/**
	 * Tries each choice in turn until a choice can successfully be matched and
	 * returns it's value
	 */
	@SafeVarargs
	public final <T> T FirstOf(Supplier<T>... choices) {
		for (Supplier<T> choice : choices) {
			int index = getParsingContext().getIndex();
			try {
				return choice.get();
			} catch (NoMatchException e) {
				// swallow, restore index
				getParsingContext().setIndex(index);
			}
		}
		throw new NoMatchException();
	}

	/**
	 * Repeat matching the term until it fails. Succeeds even if the term never
	 * matches.
	 */
	public final void ZeroOrMore(Runnable term) {
		while (true) {
			int index = getParsingContext().getIndex();
			try {
				term.run();
			} catch (NoMatchException e) {
				// swallow, restore index, break loop
				getParsingContext().setIndex(index);
				break;
			}
		}
	}

	/**
	 * Repeat matching the term until it fails. Succeeds even if the term never
	 * matches. The return values of the terms are collected and returned.
	 */
	public final <T> Collection<T> ZeroOrMore(Supplier<T> term) {
		ArrayList<T> parts = new ArrayList<>();
		while (true) {
			int index = getParsingContext().getIndex();
			try {
				parts.add(term.get());
			} catch (NoMatchException e) {
				// swallow, restore index, break loop
				getParsingContext().setIndex(index);
				break;
			}
		}
		return parts;
	}

	public final void Optional(Runnable term) {
		Optional(() -> {
			term.run();
			return null;
		}, () -> null);
	}

	public final <T> T Optional(Supplier<T> term, T fallback) {
		return Optional(term, () -> fallback);
	}

	public final <T> T Optional(Supplier<T> term, Supplier<T> fallback) {
		int index = getParsingContext().getIndex();
		try {
			return term.get();
		} catch (NoMatchException e) {
			// swallow, restore index, break loop
			getParsingContext().setIndex(index);
			return fallback.get();
		}
	}

	public final String OneOrMoreChars(Predicate<Integer> criteria,
			String expectation) {
		String result = ZeroOrMoreChars(criteria);
		if (result.isEmpty()) {
			throw new NoMatchException(getParsingContext(), expectation);
		}
		return result;
	}

	public final String ZeroOrMoreChars(Predicate<Integer> criteria) {
		StringBuilder sb = new StringBuilder();
		while (!ctx.isEOI()) {
			int index = ctx.getIndex();
			int next = ctx.next();
			if (criteria.test(next)) {
				sb.appendCodePoint(next);
			} else {
				ctx.setIndex(index);
				break;
			}
		}
		return sb.toString();
	}

	public final <T> Collection<T> OneOrMore(Supplier<T> term)
			throws NoMatchException {
		ArrayList<T> parts = new ArrayList<>();
		while (true) {
			int index = getParsingContext().getIndex();
			try {
				parts.add(term.get());

			} catch (NoMatchException e) {
				// swallow, restore index, break loop
				getParsingContext().setIndex(index);
				break;
			}
		}
		if (parts.isEmpty()) {
			throw new NoMatchException();
		}
		return parts;
	}

	public final void OneOrMore(Runnable term) {
		boolean found = false;
		while (true) {
			int index = getParsingContext().getIndex();
			try {
				term.run();
				found = true;
			} catch (NoMatchException e) {
				// swallow, restore index, break loop
				getParsingContext().setIndex(index);
				break;
			}
		}
		if (!found) {
			throw new NoMatchException();
		}
	}

	public final <T> T Try(String expectation, Supplier<T> term) {
		getParsingContext().pushExpectationFrame();
		boolean failed = false;
		try {
			return term.get();
		} catch (NoMatchException e) {
			failed = true;
			getParsingContext().popExpectationFrame(expectation);
			throw e;
		} finally {
			if (!failed)
				getParsingContext().popExpectationFrame();
		}
	}

	public final String AnyChar() {
		return new String(Character.toChars(getParsingContext().next()));
	}

	private boolean matchString(String expected) {
		OfInt it = expected.codePoints().iterator();
		while (it.hasNext()) {
			if (it.nextInt() != getParsingContext().next()) {
				return false;
			}
		}
		return true;
	}

	public final String String(String expected) {
		if (!matchString(expected))
			throw new NoMatchException(getParsingContext(), expected);
		else
			return expected;
	}

	public final <T> T String(String expected, T result) {
		if (!matchString(expected))
			throw new NoMatchException(getParsingContext(), expected);
		else
			return result;
	}

	/**
	 * Match the input at the current position against the expected string. If
	 * the input matches, use the result supplier to return the result
	 */
	public final <T> T String(String expected, Supplier<T> result) {
		if (!matchString(expected))
			throw new NoMatchException(getParsingContext(), "string <"
					+ expected + ">");
		else
			return result.get();
	}

	public final String Char(Predicate<Integer> predicate,
			java.lang.String expectation) {
		int cp = getParsingContext().next();
		if (predicate.test(cp)) {
			return new String(Character.toChars(cp));
		} else {
			throw new NoMatchException(getParsingContext(), expectation);
		}

	}

	public final String CharRange(int first, int last) {
		int cp = getParsingContext().next();
		if (cp >= first && cp <= last) {
			return new String(Character.toChars(cp));
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("character between ");
			sb.appendCodePoint(cp);
			sb.append(" and ");
			sb.appendCodePoint(last);
			throw new NoMatchException(getParsingContext(), sb.toString());
		}
	}

	public final ParsingContext getParsingContext() {
		return ctx;
	}
}
