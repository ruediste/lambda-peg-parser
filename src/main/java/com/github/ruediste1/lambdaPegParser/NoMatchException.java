package com.github.ruediste1.lambdaPegParser;

public class NoMatchException extends Error {
	private static final long serialVersionUID = 1L;
	private final ParsingContext<?> ctx;

	public NoMatchException(ParsingContext<?> ctx, int index, String expected) {
		this.ctx = ctx;
		ctx.registerExpectation(index, expected);
	}

	public NoMatchException(ParsingContext<?> ctx, String expected) {
		this.ctx = ctx;
		ctx.registerExpectation(expected);
	}

	public NoMatchException() {
		ctx = null;
	}

	@Override
	public String getMessage() {
		if (ctx == null)
			return "unregistered error";
		return ctx.getErrorMessage();
	}

}
