package com.github.ruediste1.lambdaPegParser;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Utility class to trace the rule execution of a {@link ParsingContext}
 */
public class Tracer {

	private PrintWriter target;

	public Tracer(ParsingContext<?> ctx, Writer target) {
		this(ctx, new PrintWriter(target));
	}

	public Tracer(ParsingContext<?> ctx, PrintWriter target) {
		this.target = target;
		register(ctx);
	}

	public Tracer(ParsingContext<?> ctx, OutputStream out) {
		this(ctx, new PrintWriter(out));
	}

	private int depth;

	private void indent() {
		indent(depth);
	}

	private void indent(int depth) {
		for (int i = 0; i < depth; i++) {
			target.append("  ");
		}
	}

	private void register(ParsingContext<?> ctx) {
		ctx.contentSetEvent.register(content -> {
			depth = 0;
		});

		ctx.enteringEvent.register(info -> {
			indent();
			target.println(info.parserClass.getName() + "." + info.methodName
					+ " Entering, index: " + info.index);
			depth++;
			target.flush();
		});

		ctx.failedEvent.register(info -> {
			depth--;
			indent();
			target.println(info.parserClass.getName() + "." + info.methodName
					+ " Failed, index: " + info.index);
			target.flush();
		});
		ctx.leavingEvent.register(info -> {
			depth--;
			indent();
			target.println(info.parserClass.getName() + "." + info.methodName
					+ " Leaving, index: " + info.index);
			target.flush();
		});
		ctx.recursiveEvent.register(info -> {
			indent(depth + 1);
			target.println(info.parserClass.getName() + "." + info.methodName
					+ " recursive, advancing to: " + info.index);
			target.flush();
		});
		ctx.retryingEvent.register(info -> {
			indent(depth - 1);
			target.println(info.parserClass.getName() + "." + info.methodName
					+ " Retrying, was at index: " + info.index);
			target.flush();
		});

		ctx.expectationRegistered.register(e -> {
			indent();
			target.println("index " + e.index + " unmet expectation: "
					+ e.expectation);
			target.flush();
		});
	}
}
