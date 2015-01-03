package com.github.ruediste1.lambdaPegParser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.ruediste1.lambdaPegParser.ParsingContext.ErrorDesciption;
import com.github.ruediste1.lambdaPegParser.ParsingContext.StateSnapshot;

public class ParsingContextTest {

	@Test
	public void testGetErrorLineUnderline() throws Exception {
		ErrorDesciption desc = new ErrorDesciption();
		desc.errorLine = "abc";
		desc.indexInErrorLine = 0;
		assertEquals("*--", desc.getErrorLineUnderline('-', '*'));
		desc.indexInErrorLine = 1;
		assertEquals("-*-", desc.getErrorLineUnderline('-', '*'));
		desc.indexInErrorLine = 2;
		assertEquals("--*", desc.getErrorLineUnderline('-', '*'));
	}

	@Test
	public void testFillLineInfo() throws Exception {
		ErrorDesciption desc = new ErrorDesciption();
		desc.errorPosition = 0;
		desc.fillLineInfo("ab\nc");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(0, desc.indexInErrorLine);

		desc.errorPosition = 1;
		desc.fillLineInfo("ab\ncd");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(1, desc.indexInErrorLine);

		desc.errorPosition = 2;
		desc.fillLineInfo("ab\ncd");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(2, desc.indexInErrorLine);

		desc.errorPosition = 3;
		desc.fillLineInfo("ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(0, desc.indexInErrorLine);

		desc.errorPosition = 4;
		desc.fillLineInfo("ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(1, desc.indexInErrorLine);

		desc.errorPosition = 5;
		desc.fillLineInfo("ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(2, desc.indexInErrorLine);
	}

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
