package com.github.ruediste.lambdaPegParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PrimitiveIterator.OfInt;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.github.ruediste.lambdaPegParser.ParsingContext.ExpectationFrame;
import com.github.ruediste.lambdaPegParser.ParsingContext.StateSnapshot;

/**
 * Base class for parser classes.
 * 
 * <p>
 * To define a grammar, create a derived class. Each method in the derived class
 * represents a grammar rule. The rule methods can return arbitrary results. The
 * parser class can be instantiated via
 * {@link ParserFactory#create(Class, String)}
 * </p>
 */
public class Parser<TCtx extends ParsingContext<?>> {

    private final TCtx ctx;

    protected HashMap<RuleInvocation, RuleInvocation> currentMethods = new HashMap<>();

    public static class RuleCacheKey {
        public int methodNr;
        public Object[] args;
        public ParsingState<?> state;

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(args), state, methodNr);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            RuleCacheKey other = (RuleCacheKey) obj;
            return Objects.equals(state, other.state) && methodNr == other.methodNr && Arrays.equals(args, other.args);
        }

        @Override
        public String toString() {
            return "(methodNr: " + methodNr + " state: " + state + " args: " + Arrays.toString(args) + ")";
        }
    }

    public static class RuleCacheValue {
        public Object result;
        public Throwable exception;
        public StateSnapshot snapshot;

        @Override
        public String toString() {
            return "(result: " + result + " exception: " + exception + ")";
        }
    }

    protected Map<RuleCacheKey, RuleCacheValue> ruleCache = new HashMap<>();

    /**
     * Flag set to true when a recursive invocation is encountered. While true,
     * no rule results will be cached. Cleared when handling the recursive
     * invocation.
     */
    protected boolean resultIsRecursive;

    public Parser(TCtx ctx) {
        this.ctx = ctx;
    }

    /**
     * Represents a seed for handling left recursive grammars.
     */
    protected static class Seed {
        public Object value;
        public StateSnapshot snapshot;

        public Seed(Object value, StateSnapshot snapshot) {
            super();
            this.value = value;
            this.snapshot = snapshot;
        }

    }

    /**
     * The invocation of a rule. Contains the method and arguments as well as
     * the input position. Used to handle left recursive grammars.
     */
    protected static class RuleInvocation {
        public int method;
        public Object[] args;

        public boolean recursive;
        public Seed seed;
        private ParsingState<?> state;

        @Override
        public String toString() {
            return "RuleInvocation [method=" + method + ", args=" + Arrays.toString(args) + ", state=" + state
                    + ", recursive=" + recursive + ", seed=" + seed + "]";
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(args), state);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RuleInvocation other = (RuleInvocation) obj;
            return method == other.method && Arrays.equals(args, other.args) && Objects.equals(state, other.state);
        }

        public RuleInvocation(int method, Object[] args, ParsingState<?> state) {
            super();
            this.method = method;
            this.args = args;
            this.state = state;
            this.state = state;
        }
    }

    /**
     * Matches the end of the input
     */
    public final void EOI() {
        if (ctx.hasNext()) {
            throw ctx.noMatch("End Of Input");
        }
    }

    /**
     * Match the given runnable. In any case, the input position is restored
     * after matching. If the runnable matches, a {@link NoMatchException} is
     * raised.
     */
    public void Not(Runnable runnable, String expectation) {
        StateSnapshot snapshot = ctx.snapshot();
        boolean success = false;
        try {
            runnable.run();
            success = true;
        } catch (NoMatchException e) {
            // swallow
        } finally {
            snapshot.restore();
        }
        if (success)
            throw ctx.noMatch(expectation);
    }

    /**
     * Match the given runnable. The input position is not advanced.
     */
    public void Test(Runnable runnable) {
        StateSnapshot snapshot = ctx.snapshot();
        try {
            runnable.run();
        } finally {
            snapshot.restore();
        }
    }

    /**
     * Match the given runnable. The input position is not advanced.
     */
    public <T> T Test(Supplier<T> term) {
        StateSnapshot snapshot = ctx.snapshot();
        try {
            return term.get();
        } finally {
            snapshot.restore();
        }
    }

    /**
     * Tries each choice in turn until a choice can successfully be matched. If
     * a choice is null, it is ignored.
     */
    @SafeVarargs
    public final void FirstOf(Runnable... choices) {
        for (Runnable choice : choices) {
            if (choice == null)
                continue;
            StateSnapshot snapshot = ctx.snapshot();
            try {
                choice.run();
                return;
            } catch (NoMatchException e) {
                // swallow, restore
                snapshot.restore();
            }
        }
        throw ctx.noMatch();
    }

    /**
     * Return the first provided value
     */
    public final <T> T FirstValue(T first, Object... others) {
        return first;
    }

    /**
     * Run the given runnables and return the first provided value
     */
    public final <T> T FirstValue(T first, Runnable... runnables) {
        for (Runnable runnable : runnables) {
            runnable.run();
        }
        return first;
    }

    /**
     * Match the first supplier followed by the runnables and return the result
     */
    public final <T> T FirstValue(Supplier<? extends T> first, Runnable... runnables) {
        T result = first.get();
        for (Runnable runnable : runnables) {
            runnable.run();
        }
        return result;
    }

    /**
     * Match the runnable and return the result
     */
    public final <T> T LastValue(Runnable runnable, T result) {
        runnable.run();
        return result;
    }

    /**
     * Match the runnable and return the result of the supplier.
     */
    public final <T> T LastValue(Runnable runnable, Supplier<? extends T> last) {
        runnable.run();
        return last.get();
    }

    /**
     * Tries each choice in turn until a choice can successfully be matched and
     * returns it's value. If a choice is null, it is ignored.
     */
    @SafeVarargs
    public final <T> T FirstOf(Supplier<? extends T>... choices) {
        for (Supplier<? extends T> choice : choices) {
            if (choice == null)
                continue;
            StateSnapshot snapshot = ctx.snapshot();
            try {
                return choice.get();
            } catch (NoMatchException e) {
                // swallow, restore
                snapshot.restore();
            }
        }
        throw ctx.noMatch();
    }

    /**
     * Tries each choice in turn until a choice can successfully be matched and
     * returns it's value. If a choice is null, it is ignored.
     */
    public final <T> T FirstOf(Iterable<Supplier<? extends T>> choices) {
        for (Supplier<? extends T> choice : choices) {
            if (choice == null)
                continue;
            StateSnapshot snapshot = ctx.snapshot();
            try {
                return choice.get();
            } catch (NoMatchException e) {
                // swallow, restore
                snapshot.restore();
            }
        }
        throw ctx.noMatch();
    }

    /**
     * Repeat matching the term until it fails. Succeeds even if the term never
     * matches.
     */
    public final void ZeroOrMore(Runnable term) {
        while (true) {
            StateSnapshot snapshot = ctx.snapshot();
            try {
                term.run();
            } catch (NoMatchException e) {
                // swallow, restore, break loop
                snapshot.restore();
                break;
            }
        }
    }

    /**
     * Repeat matching the term until it fails. Succeeds even if the term never
     * matches. The return values of the terms are collected and returned.
     */
    public final <T> Collection<T> ZeroOrMore(Supplier<T> term) {
        ArrayList<T> parts = new ArrayList<>();
        while (true) {
            StateSnapshot snapshot = ctx.snapshot();
            try {
                parts.add(term.get());
            } catch (NoMatchException e) {
                // swallow, restore, break loop
                snapshot.restore();
                break;
            }
        }
        return parts;
    }

    /**
     * Evaluate a term with a given precedence level. While evaluating the term,
     * further precedence restrictions need a higher level to succeed.
     */
    public final <T> T Precedence(int level, Supplier<T> term) {
        ParsingState<?> state = ctx.state();
        if (state.minPrecedenceLevel > level)
            throw ctx.noMatch("term of precedence above or equal " + state.minPrecedenceLevel);
        int old = state.minPrecedenceLevel;
        ctx.state().minPrecedenceLevel = level;
        try {
            return term.get();
        } finally {
            ctx.state().minPrecedenceLevel = old;
        }
    }

    /**
     * Try to match the term. If it fails, succeed anyways
     */
    public final void Opt(Runnable term) {
        Opt(() -> {
            term.run();
            return null;
        });
    }

    /**
     * Try to match the term. If it fails, succeed anyways. If the term matches,
     * return the result, otherwise {@link java.util.Optional#empty()}
     */
    public final <T> Optional<T> Opt(Supplier<T> term) {
        StateSnapshot snapshot = ctx.snapshot();
        try {
            return Optional.ofNullable(term.get());
        } catch (NoMatchException e) {
            // swallow, restore, break loop
            snapshot.restore();
            return Optional.empty();
        }
    }

    /**
     * Match one or more chars matching the criteria. If no matching character
     * is found, report the unmet expectation.
     */
    public final String OneOrMoreChars(Predicate<Integer> criteria, String expectation) {
        String result = ZeroOrMoreChars(criteria, expectation);
        if (result.isEmpty()) {
            throw ctx.noMatch(expectation);
        }
        return result;
    }

    /**
     * Match zero or more chars matching the criteria.
     */
    public final String ZeroOrMoreChars(Predicate<Integer> criteria, String expectation) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (ctx.hasNext() && criteria.test(ctx.peek())) {
                sb.appendCodePoint(ctx.next());
            } else {
                ctx.registerExpectation(expectation);
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Match the term one ore more times. Return the results of the matched
     * terms.
     */
    public final <T> Collection<T> OneOrMore(Supplier<T> term) {
        ArrayList<T> parts = new ArrayList<>();
        while (true) {
            StateSnapshot snapshot = ctx.snapshot();
            try {
                parts.add(term.get());
            } catch (NoMatchException e) {
                // swallow, restore, break loop
                snapshot.restore();
                break;
            }
        }
        if (parts.isEmpty()) {
            throw ctx.noMatch();
        }
        return parts;
    }

    /**
     * Match the term one or more times
     */
    public final void OneOrMore(Runnable term) {
        boolean found = false;
        while (true) {
            StateSnapshot snapshot = ctx.snapshot();
            try {
                term.run();
                found = true;
            } catch (NoMatchException e) {
                // swallow, restore, break loop
                snapshot.restore();
                break;
            }
        }
        if (!found) {
            throw ctx.noMatch();
        }
    }

    public final <T> Collection<T> OneOrMore(Supplier<T> term, Runnable separator) {
        ArrayList<T> result = new ArrayList<>();
        result.add(term.get());
        result.addAll(ZeroOrMore(() -> {
            separator.run();
            return term.get();
        }));
        return result;
    }

    public final <T> Collection<T> ZeroOrMore(Supplier<T> term, Runnable separator) {
        return Opt(() -> OneOrMore(term, separator)).orElse(Collections.emptyList());
    }

    /**
     * Match the supplied term. All expectations generated while matching the
     * term are dropped. If matching the term fails, the single specified
     * expectation is registered as expected at the input position at the
     * beginning of the matching attempt.
     */
    public final void Atomic(String expectation, Runnable term) {
        Atomic(expectation, () -> {
            term.run();
            return null;
        });
    }

    /**
     * Match the supplied term. All expectations generated while matching the
     * term are dropped. If matching the term fails, the single specified
     * expectation is registered as expected at the input position at the
     * beginning of the matching attempt.
     */
    public final <T> T Atomic(String expectation, Supplier<T> term) {
        int startIdx = ctx.getIndex();
        ExpectationFrame oldFrame = ctx.getExpectationFrame();
        ctx.setNewExpectationFrame();
        try {
            return term.get();
        } catch (NoMatchException e) {
            oldFrame.registerExpectation(startIdx, expectation);
            throw e;
        } finally {
            ctx.setExpectationFrame(oldFrame);
        }
    }

    /**
     * Match the supplied term. All expectations generated while matching the
     * term are dropped. If matching the term fails, the single specified
     * expectation is registered as expected at the input position farthest to
     * the right which has been reached.
     */
    public final <T> T Expect(String expectation, Supplier<T> term) {
        ExpectationFrame oldFrame = ctx.getExpectationFrame();
        ExpectationFrame newFrame = ctx.setNewExpectationFrame();
        try {
            return term.get();
        } catch (NoMatchException e) {
            ctx.setExpectationFrame(oldFrame);
            oldFrame = null;
            ctx.registerExpectation(expectation, newFrame.index);
            throw e;
        } finally {
            if (oldFrame != null)
                ctx.setExpectationFrame(oldFrame);
        }
    }

    /**
     * Match any character. The returned string contains the matched unicode
     * character, as one or two chars (for surrogate pairs)
     */
    public final String AnyChar() {
        if (!ctx.hasNext())
            throw ctx.noMatch("any character");
        return new String(Character.toChars(ctx.next()));
    }

    /**
     * Helper method matching a string. Returns false if the string could not be
     * found.
     */
    private boolean matchString(String expected) {
        OfInt it = expected.codePoints().iterator();
        while (it.hasNext() && ctx.hasNext()) {
            if (it.nextInt() != ctx.peek()) {
                return false;
            }
            ctx.next();
        }

        return !it.hasNext();
    }

    /**
     * Match a String. The matched string is returned.
     */
    public final String Str(String expected) {
        int startIndex = ctx.getIndex();
        if (!matchString(expected))
            throw ctx.noMatch(expected, startIndex);
        else
            return expected;
    }

    /**
     * Match a String. The provided result is returned.
     */
    public final <T> T Str(String expected, T result) {
        int startIndex = ctx.getIndex();
        if (!matchString(expected))
            throw ctx.noMatch(expected, startIndex);
        else
            return result;
    }

    /**
     * Match the input at the current position against the expected string. If
     * the input matches, use the result supplier to return the result
     */
    public final <T> T Str(String expected, Supplier<T> result) {
        int startIndex = ctx.getIndex();
        if (!matchString(expected))
            throw ctx.noMatch(expected, startIndex);
        else
            return result.get();
    }

    /**
     * Match a character using the given predicate which is evaluated against
     * the next code point in the input. If the match fails, the specified
     * expectation is reported.
     */
    public final String Char(Predicate<Integer> predicate, String expectation) {
        int startIndex = ctx.getIndex();
        if (ctx.hasNext()) {
            int cp = ctx.next();
            if (predicate.test(cp)) {
                return new String(Character.toChars(cp));
            }
        }
        throw ctx.noMatch(expectation, startIndex);

    }

    /**
     * Match all chars except the ones specified
     */
    public final String NoneOf(String chars) {
        int startIndex = ctx.getIndex();
        if (ctx.hasNext()) {
            int cp = ctx.next();
            if (chars.codePoints().allMatch(x -> x != cp)) {
                return String.valueOf(Character.toChars(cp));
            }
        }
        throw ctx.noMatch("any char except " + chars, startIndex);
    }

    /**
     * Match all characters in a given range (inclusive). Return a string
     * containing only the matched character.
     */
    public final String CharRange(int first, int last) {
        int startIndex = ctx.getIndex();
        if (ctx.hasNext()) {
            int cp = ctx.next();
            if (cp >= first && cp <= last) {
                return new String(Character.toChars(cp));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("character between ");
        sb.appendCodePoint(first);
        sb.append(" and ");
        sb.appendCodePoint(last);
        throw ctx.noMatch(sb.toString(), startIndex);
    }

    public TCtx getParsingContext() {
        return ctx;
    }

    /**
     * get the current position
     */
    public PositionInfo pos() {
        return ctx.currentPositionInfo();
    }

    @Override
    public java.lang.String toString() {
        PositionInfo info = ctx.currentPositionInfo();
        return getClass().getSimpleName() + " line: " + info.getLineNr() + "\n" + info.getLine() + "\n"
                + info.getUnderline();
    }
}
