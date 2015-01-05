package com.github.ruediste1.lambdaPegParser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineInfoTest {

	@Test
		public void testGetUnderline() throws Exception {
			LineInfo info = new LineInfo("abc", 0);
			assertEquals("*--", info.getUnderline('-', '*'));
			info = new LineInfo("abc", 1);
			assertEquals("-*-", info.getUnderline('-', '*'));
			info = new LineInfo("abc", 2);
			assertEquals("--*", info.getUnderline('-', '*'));
		}

	@Test
	public void testFillLineInfo() throws Exception {
		LineInfo desc = new LineInfo("ab\nc", 0);
		assertEquals("ab", desc.getLine());
		assertEquals(1, desc.getLineNr());
		assertEquals(0, desc.getIndexInLine());

		desc = new LineInfo("ab\ncd", 1);
		assertEquals("ab", desc.getLine());
		assertEquals(1, desc.getLineNr());
		assertEquals(1, desc.getIndexInLine());

		desc = new LineInfo("ab\ncd", 2);
		assertEquals("ab", desc.getLine());
		assertEquals(1, desc.getLineNr());
		assertEquals(2, desc.getIndexInLine());

		desc = new LineInfo("ab\ncd", 3);
		assertEquals("cd", desc.getLine());
		assertEquals(2, desc.getLineNr());
		assertEquals(0, desc.getIndexInLine());

		desc = new LineInfo("ab\ncd", 4);
		assertEquals("cd", desc.getLine());
		assertEquals(2, desc.getLineNr());
		assertEquals(1, desc.getIndexInLine());

		desc = new LineInfo("ab\ncd", 5);
		assertEquals("cd", desc.getLine());
		assertEquals(2, desc.getLineNr());
		assertEquals(2, desc.getIndexInLine());

		desc = new LineInfo("漢字", 1);
		assertEquals("漢字", desc.getLine());
		assertEquals(1, desc.getLineNr());
		assertEquals(1, desc.getIndexInLine());
		assertEquals("-*", desc.getUnderline('-', '*'));
	}
}
