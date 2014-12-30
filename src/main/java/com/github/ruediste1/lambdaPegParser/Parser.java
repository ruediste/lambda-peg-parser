package com.github.ruediste1.lambdaPegParser;


import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;

import net.sf.cglib.proxy.*;

public class Parser {

	private final ParsingContext ctx;

	public Parser(ParsingContext ctx) {
		this.ctx = ctx;
	}

	private static class Seed {
		Object value;
		int index;

		public Seed(Object value, int index) {
			super();
			this.value = value;
			this.index = index;
		}

	}

	private static class RuleInvocation {
		public Method method;
		public Object[] args;
		public int position;

		public boolean recursive;
		public Seed seed;

		@Override
		public int hashCode() {
			return Objects.hash(method, args, position);
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
					&& Objects.equals(args, other.args)
					&& Objects.equals(position, other.position);
		}

		public RuleInvocation(Method method, Object[] args, int position) {
			super();
			this.method = method;
			this.args = args;
			this.position = position;
		}

	}

	public static <T extends Parser> T create(Class<T> cls, String input) {
		ParsingContext ctx = new ParsingContext(input);
		return create(cls, ctx);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Parser> T create(Class<T> cls, ParsingContext ctx) {
		Enhancer e = new Enhancer();
		e.setSuperclass(cls);

		HashMap<RuleInvocation, RuleInvocation> currentMethods = new HashMap<>();
		e.setCallback(new MethodInterceptor() {

			@Override
			public Object intercept(Object obj, Method method, Object[] args,
					MethodProxy proxy) throws Throwable {
				if (Parser.class.equals(method.getDeclaringClass())) {
					return proxy.invokeSuper(obj, args);
				}

				RuleInvocation pair = new RuleInvocation(method, args, ctx
						.getIndex());
				{
					RuleInvocation existing = currentMethods.get(pair);
					if (existing != null) {
						// We ran into a left recursion.
						// Mark the fact and return the seed if present
						existing.recursive = true;
						if (existing.seed != null) {
							ctx.setIndex(existing.seed.index);
							return existing.seed.value;
						} else {
							throw new NoMatchException();
						}
					} else {
						currentMethods.put(pair, pair);
					}
				}
				ctx.entering(method);
				boolean failed = false;
				try {
					// first rule evaluation
					int startIndex = ctx.getIndex();
					Object result = proxy.invokeSuper(obj, args);

					if (pair.recursive) {
						// the evaluation was recursive, grow the seed
						while (true) {
							ctx.retrying(method);
							int progress = ctx.getIndex();
							pair.recursive = false;
							pair.seed = new Seed(result, progress);
							ctx.setIndex(startIndex);

							try {
								result = proxy.invokeSuper(obj, args);
							} catch (NoMatchException e) {
								// this evaluation failed, break, will use the
								// last seed
								break;
							}

							if (progress >= ctx.getIndex()) {
								// the evaluation did not grow the seed, break,
								// use last seed
								break;
							}
						}
						// use the last seed
						ctx.setIndex(pair.seed.index);
						result = pair.seed.value;
					}
					return result;
				} catch (Throwable t) {
					ctx.failed(method);
					failed = true;
					throw t;
				} finally {
					currentMethods.remove(pair);
					if (!failed) {
						ctx.leaving(method);
					}
				}
			}
		});
		return (T) e.create(new Class[] { ParsingContext.class },
				new Object[] { ctx });
	}

	public final void EOI() {
		if (!getParsingContext().isEOI()) {
			throw new NoMatchException();
		}
	}

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

	public final void ZeroOrMore(Runnable term) {
		ZeroOrMore(() -> {
			term.run();
			return null;
		}, a -> null);
	}

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

	public final <T, P> T ZeroOrMore(Supplier<P> term,
			Function<Collection<P>, T> combiner) {
		return combiner.apply(ZeroOrMore(term));
	}

	public final <T> T Optional(Runnable term) {
		return Optional(() -> {
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

	public final void OneOrMore(Runnable term) {
		OneOrMore(() -> {
			term.run();
			return null;
		}, coll -> null);
	}

	public final String OneOrMoreChars(Predicate<Integer> criteria) {
		String result = ZeroOrMoreChars(criteria);
		if (result.isEmpty()) {
			throw new NoMatchException();
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

	public final <T, P> T OneOrMore(Supplier<P> term,
			Function<Collection<P>, T> combiner) {
		return combiner.apply(OneOrMore(term));
	}

	public final <T> ArrayList<T> OneOrMore(Supplier<T> term)
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

	public final String AnyChar() {
		return new String(Character.toChars(getParsingContext().next()));
	}

	public final String Chars(String chars) {
		return Chars(chars, chars);
	}

	public final <T> T Chars(String chars, T result) {
		return Chars(chars, () -> result);
	}

	public final <T> T Chars(String chars, Supplier<T> result) {
		chars.codePoints().forEachOrdered(cp -> {
			if (cp != getParsingContext().next()) {
				throw new NoMatchException();
			}
		});
		return result.get();
	}

	public final String Char(Predicate<Integer> predicate) {
		int cp = getParsingContext().next();
		if (predicate.test(cp)) {
			return new String(Character.toChars(cp));
		} else {
			throw new NoMatchException();
		}

	}

	public final String CharRange(char first, char last) {
		int cp = getParsingContext().next();
		if (cp >= first && cp <= last) {
			return new String(Character.toChars(cp));
		} else {
			throw new NoMatchException();
		}
	}

	public final ParsingContext getParsingContext() {
		return ctx;
	}
}
