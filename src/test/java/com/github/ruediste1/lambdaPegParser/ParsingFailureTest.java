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

        String oneOrMoreChars() {
            return OneOrMoreChars(Character::isLetter, "identifier");
        }

        String atomic() {
            return Atomic("atomic", () -> String("a") + String("b"));
        }

        String expect() {
            return Expect("expect", () -> String("a") + String("b"));
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

    @Test
    public void oneOrMoreChars() {
        expectFailure("1", parser::oneOrMoreChars, 0, "identifier");
        expectFailure("", parser::oneOrMoreChars, 0, "identifier");
    }

    @Test
    public void atomic() {
        expectFailure("a", parser::atomic, 0, "atomic");
        expectFailure("", parser::atomic, 0, "atomic");
    }

    @Test
    public void expect() {
        expectFailure("a", parser::expect, 1, "expect");
        expectFailure("", parser::expect, 0, "expect");
    }

    private void expectFailure(String content, Runnable runnable,
            int failureIndex, String... expectations) {
        ctx.setContent(content);
        try {
            runnable.run();
            fail("Expected failure");
        } catch (NoMatchException e) {
            assertEquals("failure index", failureIndex,
                    ctx.getExpectationFrame().index);
            assertEquals(new HashSet<String>(Arrays.asList(expectations)),
                    ctx.getExpectationFrame().expectations);
        }
    }
}
