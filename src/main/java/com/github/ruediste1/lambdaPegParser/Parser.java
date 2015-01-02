package com.github.ruediste1.lambdaPegParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.github.ruediste1.lambdaPegParser.ParsingContext.StateSnapshot;

/**
 * Base class for parser classes.
 * 
 * <p>
 * To define a grammar, create a derived class. Each method in the derived class
 * represents a grammar rule. The rule methods can return arbitrary results. The
 * parser class can be instantiated via
 * {@link ParserFactory#create(Class, String)}
 * </p>
 */
public class Parser<TCtx extends ParsingContext<?>> {

	private final TCtx ctx;

	protected HashMap<RuleInvocation, RuleInvocation> currentMethods = new HashMap<>();

	public Parser(TCtx ctx) {
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
		if (!ctx.isEOI()) {
			throw new NoMatchException(ctx, "End Of Input");
		}
	}

	/**
	 * Tries each choice in turn until a choice can successfully be matched
	 */
	@SafeVarargs
	public final void FirstOf(Runnable... choices) {
		for (Runnable choice : choices) {
			StateSnapshot snapshot = ctx.snapshot();
			try {
				choice.run();
				return;
			} catch (NoMatchException e) {
				// swallow, restore
				snapshot.restore();
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
			StateSnapshot snapshot = ctx.snapshot();
			try {
				return choice.get();
			} catch (NoMatchException e) {
				// swallow, restore
				snapshot.restore();
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
			StateSnapshot snapshot = ctx.snapshot();
			try {
				term.run();
			} catch (NoMatchException e) {
				// swallow, restore, break loop
				snapshot.restore();
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
			StateSnapshot snapshot = ctx.snapshot();
			try {
				parts.add(term.get());
			} catch (NoMatchException e) {
				// swallow, restore, break loop
				snapshot.restore();
				break;
			}
		}
		return parts;
	}

	/**
	 * Try to match the term. If it fails, succeed anyways
	 */
	public final void Optional(Runnable term) {
		Optional(() -> {
			term.run();
			return null;
		});
	}

	/**
	 * Try to match the term. If it fails, succeed anyways. If the term matches,
	 * return the result, otherwise {@link java.util.Optional#empty()}
	 */
	public final <T> Optional<T> Optional(Supplier<T> term) {
		StateSnapshot snapshot = ctx.snapshot();
		try {
			return Optional.of(term.get());
		} catch (NoMatchException e) {
			// swallow, restore, break loop
			snapshot.restore();
			return Optional.empty();
		}
	}

	/**
	 * Match one or more chars matching the criteria. If no matching character
	 * is found, report the unmet expectation.
	 */
	public final String OneOrMoreChars(Predicate<Integer> criteria,
			String expectation) {
		String result = ZeroOrMoreChars(criteria);
		if (result.isEmpty()) {
			throw new NoMatchException(ctx, expectation);
		}
		return result;
	}

	/**
	 * Match zero or more chars matching the criteria.
	 */
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

	/**
	 * Match the term one ore more times. Return the results of the matched
	 * terms.
	 */
	public final <T> Collection<T> OneOrMore(Supplier<T> term)
			throws NoMatchException {
		ArrayList<T> parts = new ArrayList<>();
		while (true) {
			StateSnapshot snapshot = ctx.snapshot();
			try {
				parts.add(term.get());

			} catch (NoMatchException e) {
				// swallow, restore, break loop
				snapshot.restore();
				break;
			}
		}
		if (parts.isEmpty()) {
			throw new NoMatchException();
		}
		return parts;
	}

	/**
	 * Match the term one or more times
	 */
	public final void OneOrMore(Runnable term) {
		boolean found = false;
		while (true) {
			StateSnapshot snapshot = ctx.snapshot();
			try {
				term.run();
				found = true;
			} catch (NoMatchException e) {
				// swallow, restore, break loop
				snapshot.restore();
				break;
			}
		}
		if (!found) {
			throw new NoMatchException();
		}
	}

	/**
	 * Try matching the supplied term. If the term can not successfully be
	 * parsed and it does not consume any input, all expectations generated
	 * while matching the term are replaced with the single specified
	 * expectation.
	 */
	public final <T> T Try(String expectation, Supplier<T> term) {
		int startIdx = ctx.getIndex();
		ctx.pushExpectationFrame();
		try {
			return term.get();
		} finally {
			ctx.popExpectationFrame(startIdx, expectation);
		}
	}

	/**
	 * Match any character. The returned string contains the matched unicode
	 * character, as one or two chars (for surrogate pairs)
	 */
	public final String AnyChar() {
		return new String(Character.toChars(ctx.next()));
	}

	/**
	 * Helper method matching a string. Returns false if the string could not be
	 * found.
	 */
	private boolean matchString(String expected) {
		OfInt it = expected.codePoints().iterator();
		while (it.hasNext()) {
			if (it.nextInt() != ctx.next()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Match a String. The matched string is returned.
	 */
	public final String String(String expected) {
		if (!matchString(expected))
			throw new NoMatchException(ctx, expected);
		else
			return expected;
	}

	/**
	 * Match a String. The provided result is returned.
	 */
	public final <T> T String(String expected, T result) {
		if (!matchString(expected))
			throw new NoMatchException(ctx, expected);
		else
			return result;
	}

	/**
	 * Match the input at the current position against the expected string. If
	 * the input matches, use the result supplier to return the result
	 */
	public final <T> T String(String expected, Supplier<T> result) {
		if (!matchString(expected))
			throw new NoMatchException(ctx, "string <" + expected + ">");
		else
			return result.get();
	}

	/**
	 * Match a character using the given predicate which is evaluated against
	 * the next code point in the input. If the match fails, the specified
	 * expectation is reported.
	 */
	public final String Char(Predicate<Integer> predicate, String expectation) {
		int cp = ctx.next();
		if (predicate.test(cp)) {
			return new String(Character.toChars(cp));
		} else {
			throw new NoMatchException(ctx, expectation);
		}

	}

	/**
	 * Match all characters in a given range (inclusive). Return a string
	 * containing only the matched character.
	 */
	public final String CharRange(int first, int last) {
		int cp = ctx.next();
		if (cp >= first && cp <= last) {
			return new String(Character.toChars(cp));
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("character between ");
			sb.appendCodePoint(cp);
			sb.append(" and ");
			sb.appendCodePoint(last);
			throw new NoMatchException(ctx, sb.toString());
		}
	}

	public TCtx getParsingContext() {
		return ctx;
	}
}
