package com.github.ruediste1.lambdaPegParser;

/**
 * Prototype of the advice code used to transform the parser classes
 */
public class PrototypeParser extends Parser<ParsingContext<?>> {

	public PrototypeParser(ParsingContext<?> ctx) {
		super(ctx);
	}

	public Object sampleRule() {
		return null;

	}

	private static int getMethodNumber() {
		return 1;
	}

	private static Object[] getArgs() {
		return null;
	}

	private static String getMethodName() {
		return null;
	}

	public Object prototypeAdvice() {
		ParsingContext<?> ctx = getParsingContext();

		RuleInvocation pair = new RuleInvocation(getMethodNumber(), getArgs(),
				ctx.getIndex());

		// check for left recursions
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

		ctx.entering(getClass(), getMethodName());
		boolean failed = false;
		try {
			// first rule evaluation
			int startIndex = ctx.getIndex();
			int progress = startIndex;
			Object result;

			while (true) {

				try {
					result = sampleRule();
				} catch (NoMatchException e) {
					if (pair.seed != null) {
						// this evaluation failed, break, use the
						// last seed
						ctx.setIndex(pair.seed.index);
						result = pair.seed.value;
						break;
					} else
						throw e;
				}

				if (pair.recursive) {
					// the invocation resulted in an recursion, grow the
					// seed

					if (pair.seed != null && progress >= ctx.getIndex()) {
						// the evaluation did not grow the seed, break,
						// use last seed

						ctx.setIndex(pair.seed.index);
						result = pair.seed.value;

						break;
					}

					progress = ctx.getIndex();
					ctx.retrying(getClass(), getMethodName());
					pair.recursive = false;
					pair.seed = new Seed(result, progress);
					ctx.setIndex(startIndex);
				} else {
					// no recursion, we are done
					break;
				}

			}

			return result;
		} catch (Throwable t) {
			ctx.failed(getClass(), getMethodName());
			failed = true;
			throw t;
		} finally {
			currentMethods.remove(pair);
			if (!failed) {
				ctx.leaving(getClass(), getMethodName());
			}
		}
	}
}
