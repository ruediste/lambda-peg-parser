package com.github.ruediste1.lambdaPegParser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.ruediste1.lambdaPegParser.ParsingContext.ErrorDesciption;

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
		ParsingContext.fillLineInfo(desc, "ab\nc");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(0, desc.indexInErrorLine);

		desc.errorPosition = 1;
		ParsingContext.fillLineInfo(desc, "ab\ncd");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(1, desc.indexInErrorLine);

		desc.errorPosition = 2;
		ParsingContext.fillLineInfo(desc, "ab\ncd");
		assertEquals("ab", desc.errorLine);
		assertEquals(1, desc.errorLineNr);
		assertEquals(2, desc.indexInErrorLine);

		desc.errorPosition = 3;
		ParsingContext.fillLineInfo(desc, "ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(0, desc.indexInErrorLine);

		desc.errorPosition = 4;
		ParsingContext.fillLineInfo(desc, "ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(1, desc.indexInErrorLine);

		desc.errorPosition = 5;
		ParsingContext.fillLineInfo(desc, "ab\ncd");
		assertEquals("cd", desc.errorLine);
		assertEquals(2, desc.errorLineNr);
		assertEquals(2, desc.indexInErrorLine);
	}

}
