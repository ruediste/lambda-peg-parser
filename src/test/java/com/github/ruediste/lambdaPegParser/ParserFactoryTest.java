package com.github.ruediste.lambdaPegParser;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;

import com.github.ruediste.lambdaPegParser.DefaultParser;
import com.github.ruediste.lambdaPegParser.DefaultParsingContext;
import com.github.ruediste.lambdaPegParser.NoMatchException;
import com.github.ruediste.lambdaPegParser.Parser;
import com.github.ruediste.lambdaPegParser.ParserFactory;
import com.github.ruediste.lambdaPegParser.ParsingContext;
import com.github.ruediste.lambdaPegParser.Tracer;

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
            return "(" + Term()
                    + ZeroOrMore(() -> FirstOf(() -> Str("+", " plus "), () -> Str("-", " minus ")) + Term() + ")")
                            .stream().collect(Collectors.joining());
        }

        String Term() {
            return "(" + Factor()
                    + ZeroOrMore(() -> FirstOf(() -> Str("*", " mul "), () -> Str("/", " div")) + Factor()).stream()
                            .collect(Collectors.joining())
                    + ")";
        }

        String Factor() {
            return FirstOf(this::Number, () -> Parens());
        }

        String Parens() {
            return Str("(", "(") + Expression() + Str(")", ")");
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
            return FirstOf(() -> Str("+", () -> left + Term()), () -> Str("-", () -> left - Term()));
        }

        int Term() {
            int left = Factor();

            Collection<Function<Integer, Integer>> funcs = ZeroOrMore(
                    () -> this.<Function<Integer, Integer>> FirstOf(() -> {
                        Str("*");
                        int right = Factor();
                        return x -> x * right;
                    } , () -> {
                        Str("/");
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
            Str("(");
            int result = Expression();
            Str(")");
            return result;
        }

        int Number() {
            return Integer.valueOf(OneOrMore(this::Digit).stream().collect(joining()));
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
            return Expect("product",
                    () -> "(" + expr() + ")"
                            + OneOrMore(() -> FirstOf(() -> Str("*"), () -> Str("/")) + "(" + expr() + ")").stream()
                                    .collect(joining()));
        }

        String sum() {
            return Expect("sum",
                    () -> "(" + expr() + ")"
                            + OneOrMore(() -> FirstOf(() -> Str("+"), () -> Str("-")) + "(" + expr() + ")").stream()
                                    .collect(joining()));
        }

        String value() {
            return FirstOf(() -> OneOrMoreChars(Character::isDigit, "digit"), () -> Str("(") + expr() + Str(")"));
        }
    }

    public interface ISmallRecursiveParser {
        String input();
    }

    static class RulesWithArgumentsParser extends DefaultParser {
        public RulesWithArgumentsParser(DefaultParsingContext ctx) {
            super(ctx);
        }

        void ruleWithArguments(int a, int b) {
            System.out.println("a: " + a + " b:" + b);
        }
    }

    static class InnerClassParser extends DefaultParser {
        public InnerClassParser(DefaultParsingContext ctx) {
            super(ctx);
        }

        private class InnerClass {
        }

        void rule() {
            new InnerClass();
            new Function<String, String>() {

                @Override
                public java.lang.String apply(java.lang.String t) {
                    return null;
                }
            };
        }
    }

    static class SmallRecursiveParser extends DefaultParser implements ISmallRecursiveParser {
        public SmallRecursiveParser(DefaultParsingContext ctx) {
            super(ctx);
        }

        @Override
        public String input() {
            whiteSpace();
            return term();
        }

        String term() {
            String result = FirstOf(() -> term() + Str("b"), () -> Str("b"));
            whiteSpace();
            return result;
        }

        void whiteSpace() {
            ZeroOrMoreChars(Character::isWhitespace, "white space");
        }
    }

    private <T extends Parser<?>> T create(Class<T> cls, String input) {
        ParsingContext<?> ctx = ParserFactory.createParsingContext(cls, input);
        T parser = ParserFactory.create(cls, ctx);
        new Tracer(ctx, System.out);
        return parser;
    }

    @Test
    public void simpleTest() {
        SimpleParser parser = create(SimpleParser.class, "2*3+1");
        assertEquals("((2 mul 3) plus (1))", parser.InputLine());
    }

    @Test
    public void simpleTestEvaluation() {
        EvaluatingParser parser = create(EvaluatingParser.class, "2*3+1");
        assertEquals(7, parser.InputLine());
    }

    @Test
    public void simpleTestEvaluationDiv() {
        EvaluatingParser parser = create(EvaluatingParser.class, "5*1/2+1");
        assertEquals(3, parser.InputLine());
    }

    @Test
    public void recursive() {
        RecursiveParser parser = create(RecursiveParser.class, "1+2*3");
        assertEquals("(1)+((2)*(3))", parser.input());
    }

    @Test
    public void ruleWithArguments() {
        RulesWithArgumentsParser parser = create(RulesWithArgumentsParser.class, "1+2*3");
        parser.ruleWithArguments(1, 2);
    }

    @Test
    public void recursiveError() {
        try {
            create(RecursiveParser.class, "1+2%3").input();
        } catch (NoMatchException e) {
            assertEquals("Error on line 1. Expected: product, sum, End Of Input\n" + "1+2%3\n" + "   ^ ",
                    e.getMessage());
        }
    }

    @Test
    public void smallRecursive() {
        ISmallRecursiveParser parser = ParserFactory.create(SmallRecursiveParser.class, ISmallRecursiveParser.class,
                " b bb");
        assertEquals("bbb", parser.input());
    }

    @Test
    public void innerClass() {
        InnerClassParser parser = ParserFactory.create(InnerClassParser.class, " b bb");
        parser.rule();
    }

}
