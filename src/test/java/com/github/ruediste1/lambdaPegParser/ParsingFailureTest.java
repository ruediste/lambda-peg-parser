package com.github.ruediste1.lambdaPegParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

public class ParsingFailureTest {

	public static class ParsingFailureParser extends DefaultParser {

		public ParsingFailureParser(DefaultParsingContext ctx) {
			super(ctx);
		}

		String anyChar() {
			return String("a") + AnyChar();
		}

		String string() {
			return String("foo");
		}

		String matchChar() {
			return Char(Character::isLetter, "letter");
		}

		String charRange() {
			return CharRange('a', 'b');
		}
	}

	ParsingFailureParser parser;
	DefaultParsingContext ctx;

	@Before
	public void setup() {
		ctx = new DefaultParsingContext("");
		parser = ParserFactory.create(ParsingFailureParser.class, ctx);
	}

	@Test
	public void anyChar() {
		expectFailure("a", parser::anyChar, 1, "any character");
	}

	@Test
	public void string() {
		expectFailure("fo", parser::string, 0, "foo");
		expectFailure("foa ", parser::string, 0, "foo");

		ctx.setContent("foo");
		assertEquals("foo", parser.string());

		ctx.setContent("foo bar");
		assertEquals("foo", parser.string());
	}

	@Test
	public void matchChar() {
		expectFailure("1", parser::matchChar, 0, "letter");
		expectFailure("", parser::matchChar, 0, "letter");
	}

	@Test
	public void charRange() {
		expectFailure("c", parser::charRange, 0, "character between a and b");
		expectFailure("", parser::charRange, 0, "character between a and b");
	}

	private void expectFailure(String content, Runnable runnable,
			int failureIndex, String... expectations) {
		ctx.setContent(content);
		try {
			runnable.run();
			fail();
		} catch (NoMatchException e) {
			assertEquals("failure index", failureIndex,
					ctx.getExpectationsIndex());
			assertEquals(new HashSet<String>(Arrays.asList(expectations)),
					ctx.getExpectations());
		}
	}
}
