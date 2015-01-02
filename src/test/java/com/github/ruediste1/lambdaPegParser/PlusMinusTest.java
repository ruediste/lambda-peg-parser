package com.github.ruediste1.lambdaPegParser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlusMinusTest {

	/**
	 * A simple parser recognizing:
	 * 
	 * <pre>
	 * input = sum EOI
	 * sum = number ('+' number | '-' number)*
	 * number = digit +
	 * </pre>
	 */
	public static class PlusMinusParser extends DefaultParser {

		public PlusMinusParser(DefaultParsingContext ctx) {
			super(ctx);
		}

		int input() {
			int result = sum();
			EOI();
			return result;
		}

		int sum() {
			int result = number();
			return ZeroOrMore(() -> FirstOf(() -> {
				String("+");
				return number();
			}, () -> {
				String("-");
				return -number();
			})).stream().reduce(result, (a, b) -> a + b);
		}

		int number() {
			return Integer
					.parseInt(OneOrMoreChars(Character::isDigit, "number"));
		}
	}

	@Test
	public void singleNumber() {
		assertEquals(123, ParserFactory.create(PlusMinusParser.class, "123")
				.input());
	}

	@Test
	public void addition() {
		DefaultParsingContext ctx = new DefaultParsingContext("1+2");
		PlusMinusParser parser = ParserFactory.create(PlusMinusParser.class,
				ctx);
		int result = parser.input();

		assertEquals(3, result);
	}

	@Test
	public void subtraction() {
		assertEquals(7, ParserFactory.create(PlusMinusParser.class, "12-5")
				.input());
	}

	@Test
	public void complex() {
		assertEquals(9, ParserFactory.create(PlusMinusParser.class, "12-5+2")
				.input());
	}
}
