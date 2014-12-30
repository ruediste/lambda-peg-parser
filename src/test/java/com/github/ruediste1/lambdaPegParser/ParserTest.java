package com.github.ruediste1.lambdaPegParser;


import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

public class ParserTest {

	private static class SimpleParser extends Parser {

		public SimpleParser(ParsingContext ctx) {
			super(ctx);
		}

		public String InputLine() {
			String result = Expression();
			EOI();
			return result;
		}

		String Expression() {
			return "("
					+ Term()
					+ ZeroOrMore(
							() -> FirstOf(() -> Chars("+", " plus "),
									() -> Chars("-", " minus ")) + Term() + ")",
							strs -> strs.stream().collect(Collectors.joining()));
		}

		String Term() {
			return "("
					+ Factor()
					+ ZeroOrMore(
							() -> FirstOf(() -> Chars("*", " mul "),
									() -> Chars("/", " div")) + Factor(),
							strs -> strs.stream().collect(Collectors.joining()))
					+ ")";
		}

		String Factor() {
			return FirstOf(this::Number, () -> Parens());
		}

		String Parens() {
			return Chars("(", "(") + Expression() + Chars(")", ")");
		}

		String Number() {
			return OneOrMore(this::Digit,
					strs -> strs.stream().collect(joining()));
		}

		String Digit() {
			return Char(Character::isDigit);
		}
	}

	private static class EvaluatingParser extends Parser {

		public EvaluatingParser(ParsingContext ctx) {
			super(ctx);
		}

		public int InputLine() {
			int result = Expression();
			EOI();
			return result;
		}

		int Expression() {
			int left = Term();
			return FirstOf(() -> Chars("+", () -> left + Term()),
					() -> Chars("-", () -> left - Term()));
		}

		int Term() {
			int left = Factor();

			return this.<Integer, Function<Integer, Integer>> ZeroOrMore(
					() -> FirstOf(() -> {
						Chars("*");
						int right = Factor();
						return x -> x * right;
					}, () -> {
						Chars("/");
						int right = Factor();
						return x -> x / right;
					}), funcs -> {
						int result = left;
						for (Function<Integer, Integer> f : funcs) {
							result = f.apply(result);
						}
						return result;
					});
		}

		int Factor() {
			return FirstOf(this::Number, () -> Parens());
		}

		int Parens() {
			Chars("(");
			int result = Expression();
			Chars(")");
			return result;
		}

		int Number() {
			return Integer.valueOf(OneOrMore(this::Digit, strs -> strs.stream()
					.collect(joining())));
		}

		String Digit() {
			return Char(Character::isDigit);
		}
	}

	/**
	 * <pre>
	 * Expr    ← Product / Sum / Value
	 * Product ← Expr (('*' / '/') Expr)*
	 * Sum     ← Expr (('+' / '-') Expr)*
	 * Value   ← [0-9.]+ / '(' Expr ')'
	 * </pre>
	 *
	 * @author ruedi
	 *
	 */
	static class RecursiveParser extends Parser {
		public RecursiveParser(ParsingContext ctx) {
			super(ctx);
		}

		String input() {
			String result = expr();
			EOI();
			return result;
		}

		String expr() {
			return FirstOf(() -> product(), () -> sum(), () -> value());
		}

		String product() {
			return "("
					+ expr()
					+ ")"
					+ OneOrMore(
							() -> FirstOf(() -> Chars("*"), () -> Chars("/"))
									+ "(" + expr() + ")", funcs -> funcs
									.stream().collect(joining()));
		}

		String sum() {
			return "("
					+ expr()
					+ ")"
					+ OneOrMore(
							() -> FirstOf(() -> Chars("+"), () -> Chars("-"))
									+ "(" + expr() + ")", funcs -> funcs
									.stream().collect(joining()));
		}

		String value() {
			return FirstOf(() -> OneOrMoreChars(Character::isDigit),
					() -> Chars("(") + expr() + Chars(")"));
		}
	}

	static class SmallRecursiveParser extends Parser {
		public SmallRecursiveParser(ParsingContext ctx) {
			super(ctx);
		}

		String term() {
			return FirstOf(() -> term() + Chars("b"), () -> Chars("b"));
		}
	}

	@Test
	public void simpleTest() {
		ParsingContext ctx = new ParsingContext("2*3+1");
		SimpleParser parser = Parser.create(SimpleParser.class, ctx);
		assertEquals("((2 mul 3) plus (1))", parser.InputLine());
	}

	@Test
	public void simpleTestEvaluation() {
		ParsingContext ctx = new ParsingContext("2*3+1");
		EvaluatingParser parser = Parser.create(EvaluatingParser.class, ctx);
		assertEquals(7, parser.InputLine());
	}

	@Test
	public void simpleTestEvaluationDiv() {
		ParsingContext ctx = new ParsingContext("5*1/2+1");
		EvaluatingParser parser = Parser.create(EvaluatingParser.class, ctx);
		assertEquals(3, parser.InputLine());
	}

	@Test
	public void recursive() {
		ParsingContext ctx = new ParsingContext("1+2*3");
		RecursiveParser parser = Parser.create(RecursiveParser.class, ctx);
		assertEquals("(1)+((2)*(3))", parser.input());
	}

	@Test
	public void smallRecursive() {
		ParsingContext ctx = new ParsingContext("bbb");
		SmallRecursiveParser parser = Parser.create(SmallRecursiveParser.class,
				ctx);
		assertEquals("bbb", parser.term());
	}
}
