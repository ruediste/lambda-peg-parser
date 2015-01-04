package com.github.ruediste1.lambdaPegParser;

import com.github.ruediste1.lambdaPegParser.ParsingContext.StateSnapshot;

/**
 * Prototype of the advice code used to transform the parser classes
 */
public class PrototypeParser extends Parser<ParsingContext<?>> {

	public PrototypeParser(ParsingContext<?> ctx) {
		super(ctx);
	}

	/**
	 * call will be replaced with the inlined rule method
	 */
	public static Object sampleRule() {
		return null;
	}

	/**
	 * call will be replaced
	 */
	private static int getMethodNumber() {
		return 1;
	}

	/**
	 * call will be replaced
	 */
	private static Object[] getArgs() {
		return null;
	}

	/**
	 * call will be replaced
	 */
	private static Class<?>[] getArgumentTypes() {
		return null;
	}

	/**
	 * call will be replaced
	 */
	private static String getMethodName() {
		return null;
	}

	/**
	 * The bytecode of this method is placed in the method bodies of the rule
	 * methods, around the code of the original rule method.
	 */
	public Object prototypeAdvice() {
		ParsingContext<?> ctx = getParsingContext();

		RuleLoggingInfo loggingInfo = new RuleLoggingInfo();
		loggingInfo.arguments = getArgs();
		loggingInfo.methodName = getMethodName();
		loggingInfo.parserClass = getClass();
		loggingInfo.argumentTypes = getArgumentTypes();

		RuleInvocation invocation = new RuleInvocation(getMethodNumber(),
				loggingInfo.arguments, ctx.getIndex());

		// check for left recursions
		{
			RuleInvocation existing = currentMethods.get(invocation);
			if (existing != null) {
				// We ran into a left recursion.
				// Mark the fact and return the seed if present
				existing.recursive = true;
				if (existing.seed != null) {
					existing.seed.snapshot.restoreClone();
					loggingInfo.result = Var.of(existing.seed.value);
					ctx.recursive(loggingInfo);
					return existing.seed.value;
				} else {
					ctx.recursive(loggingInfo);
					throw new NoMatchException();
				}
			} else {
				currentMethods.put(invocation, invocation);
			}
		}

		ctx.entering(loggingInfo);
		boolean failed = false;
		try {
			// first rule evaluation
			int startIndex = ctx.getIndex();
			StateSnapshot startSnapshot = ctx.snapshot();
			int progress = startIndex;
			Object result;

			while (true) {

				try {
					result = sampleRule();
				} catch (NoMatchException e) {
					if (invocation.seed != null) {
						// this evaluation failed, break, use the
						// last seed
						invocation.seed.snapshot.restore();
						result = invocation.seed.value;
						break;
					} else
						throw e;
				}

				if (invocation.recursive) {
					// the invocation resulted in an recursion

					if (invocation.seed != null && progress >= ctx.getIndex()) {
						// the evaluation did not grow the seed, break,
						// use last seed
						invocation.seed.snapshot.restore();
						result = invocation.seed.value;

						break;
					}

					// recursion, grow the seed
					progress = ctx.getIndex();
					ctx.retrying(loggingInfo);
					invocation.recursive = false;
					invocation.seed = new Seed(result, ctx.snapshot());
					startSnapshot.restoreClone();
				} else {
					// no recursion, we are done
					break;
				}

			}
			loggingInfo.result = Var.of(result);
			return result;
		} catch (Throwable t) {
			ctx.failed(loggingInfo);
			failed = true;
			throw t;
		} finally {
			currentMethods.remove(invocation);
			if (!failed) {
				ctx.leaving(loggingInfo);
			}
		}
	}
}
