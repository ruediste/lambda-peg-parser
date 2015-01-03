package com.github.ruediste1.lambdaPegParser;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

public class ParserFactoryTest {

	/**
	 * Grammar:
	 * 
	 * <pre>
	 * Expression ← Term ((‘+’ / ‘-’) Term)*
	 * Term       ← Factor ((‘*’ / ‘/’) Factor)*
	 * Factor     ← Number / Parens
	 * Number     ← Digit+
	 * Parens     ← ‘(’ expression ‘)’
	 * Digit      ← [0-9]
	 * </pre>
	 */
	private static class SimpleParser extends DefaultParser {

		public SimpleParser(DefaultParsingContext ctx) {
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
							() -> FirstOf(() -> String("+", " plus "),
									() -> String("-", " minus "))
									+ Term()
									+ ")").stream().collect(
							Collectors.joining());
		}

		String Term() {
			return "("
					+ Factor()
					+ ZeroOrMore(
							() -> FirstOf(() -> String("*", " mul "),
									() -> String("/", " div")) + Factor())
							.stream().collect(Collectors.joining()) + ")";
		}

		String Factor() {
			return FirstOf(this::Number, () -> Parens());
		}

		String Parens() {
			return String("(", "(") + Expression() + String(")", ")");
		}

		String Number() {
			return OneOrMore(this::Digit).stream().collect(joining());
		}

		String Digit() {
			return Char(Character::isDigit, "digit");
		}
	}

	/**
	 * Grammar:
	 * 
	 * <pre>
	 * Expression ← Term ((‘+’ / ‘-’) Term)*
	 * Term       ← Factor ((‘*’ / ‘/’) Factor)*
	 * Factor     ← Number / Parens
	 * Number     ← Digit+
	 * Parens     ← ‘(’ expression ‘)’
	 * Digit      ← [0-9]
	 * </pre>
	 */
	private static class EvaluatingParser extends DefaultParser {

		public EvaluatingParser(DefaultParsingContext ctx) {
			super(ctx);
		}

		public int InputLine() {
			int result = Expression();
			EOI();
			return result;
		}

		int Expression() {
			int left = Term();
			return FirstOf(() -> String("+", () -> left + Term()),
					() -> String("-", () -> left - Term()));
		}

		int Term() {
			int left = Factor();

			Collection<Function<Integer, Integer>> funcs = ZeroOrMore(() -> FirstOf(
					() -> {
						String("*");
						int right = Factor();
						return x -> x * right;
					}, () -> {
						String("/");
						int right = Factor();
						return x -> x / right;
					}));

			int result = left;
			for (Function<Integer, Integer> f : funcs) {
				result = f.apply(result);
			}
			return result;
		}

		int Factor() {
			return FirstOf(this::Number, () -> Parens());
		}

		int Parens() {
			String("(");
			int result = Expression();
			String(")");
			return result;
		}

		int Number() {
			return Integer.valueOf(OneOrMore(this::Digit).stream().collect(
					joining()));
		}

		String Digit() {
			return Char(Character::isDigit, "digit");
		}
	}

	/**
	 * Grammar:
	 * 
	 * <pre>
	 * Expr    ← Product / Sum / Value
	 * Product ← Expr (('*' / '/') Expr)+
	 * Sum     ← Expr (('+' / '-') Expr)+
	 * Value   ← [0-9.]+ / '(' Expr ')'
	 * </pre>
	 *
	 * @author ruedi
	 *
	 */
	static class RecursiveParser extends DefaultParser {
		public RecursiveParser(DefaultParsingContext ctx) {
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
			return Expect(
					"product",
					() -> "("
							+ expr()
							+ ")"
							+ OneOrMore(
									() -> FirstOf(() -> String("*"),
											() -> String("/"))
											+ "("
											+ expr()
											+ ")").stream().collect(joining()));
		}

		String sum() {
			return Expect(
					"sum",
					() -> "("
							+ expr()
							+ ")"
							+ OneOrMore(
									() -> FirstOf(() -> String("+"),
											() -> String("-"))
											+ "("
											+ expr()
											+ ")").stream().collect(joining()));
		}

		String value() {
			return FirstOf(() -> OneOrMoreChars(Character::isDigit, "digit"),
					() -> String("(") + expr() + String(")"));
		}
	}

	public interface ISmallRecursiveParser {
		String input();
	}

	static class SmallRecursiveParser extends DefaultParser implements
			ISmallRecursiveParser {
		public SmallRecursiveParser(DefaultParsingContext ctx) {
			super(ctx);
		}

		@Override
		public String input() {
			whiteSpace();
			return term();
		}

		String term() {
			String result = FirstOf(() -> term() + String("b"),
					() -> String("b"));
			whiteSpace();
			return result;
		}

		void whiteSpace() {
			ZeroOrMoreChars(Character::isWhitespace, "white space");
		}
	}

	@Test
	public void simpleTest() {
		SimpleParser parser = ParserFactory.create(SimpleParser.class, "2*3+1");
		assertEquals("((2 mul 3) plus (1))", parser.InputLine());
	}

	@Test
	public void simpleTestEvaluation() {
		EvaluatingParser parser = ParserFactory.create(EvaluatingParser.class,
				"2*3+1");
		assertEquals(7, parser.InputLine());
	}

	@Test
	public void simpleTestEvaluationDiv() {
		EvaluatingParser parser = ParserFactory.create(EvaluatingParser.class,
				"5*1/2+1");
		assertEquals(3, parser.InputLine());
	}

	@Test
	public void recursive() {
		RecursiveParser parser = ParserFactory.create(RecursiveParser.class,
				"1+2*3");
		assertEquals("(1)+((2)*(3))", parser.input());
	}

	@Test
	public void recursiveError() {
		try {
			ParserFactory.create(RecursiveParser.class, "1+2%3").input();
		} catch (NoMatchException e) {
			assertEquals(
					"Error on line 1. Expected: product, sum, End Of Input\n"
							+ "1+2%3\n" + "   ^ ", e.getMessage());
		}
	}

	@Test
	public void smallRecursive() {
		ISmallRecursiveParser parser = ParserFactory.create(
				SmallRecursiveParser.class, ISmallRecursiveParser.class,
				" b bb");
		assertEquals("bbb", parser.input());
	}

}
