package com.github.ruediste.lambdaPegParser;

import org.junit.Test;

import com.github.ruediste.lambdaPegParser.DefaultParsingContext;
import com.github.ruediste.lambdaPegParser.ParsingContext.StateSnapshot;

public class ParsingContextTest {

    @Test
    public void snapshotRestore() {
        DefaultParsingContext ctx = new DefaultParsingContext("foo");
        StateSnapshot sn = ctx.snapshot();
        sn.restore();
    }

    @Test(expected = RuntimeException.class)
    public void snapshotRestoreTwice() {
        DefaultParsingContext ctx = new DefaultParsingContext("foo");
        StateSnapshot sn = ctx.snapshot();
        sn.restore();
        sn.restore();
    }

    @Test(expected = RuntimeException.class)
    public void snapshotRestoreTwiceSecondTimeClone() {
        DefaultParsingContext ctx = new DefaultParsingContext("foo");
        StateSnapshot sn = ctx.snapshot();
        sn.restore();
        sn.restoreClone();
    }

    public void snapshotRestoreCloneTwice() {
        DefaultParsingContext ctx = new DefaultParsingContext("foo");
        StateSnapshot sn = ctx.snapshot();
        sn.restoreClone();
        sn.restoreClone();
    }
}
