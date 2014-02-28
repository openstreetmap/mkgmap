/*
 * Copyright (C) 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.scan;

import java.io.StringReader;

import org.junit.Test;

import static org.junit.Assert.*;


public class TokenScannerTest {

	/**
	 * Before reading anything, the line number is zero.
	 */
	@Test
	public void testLinenumberStart() {
		String s = "hello world\nnext tokens\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		assertEquals(0, ts.getLinenumber());
	}

	/**
	 * Immediately after reading the first token the line number is incremented.
	 */
	@Test
	public void testLinenumberInc() {
		String s = "hello world\nnext tokens\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		ts.nextValue();
		assertEquals(1, ts.getLinenumber());

		// still on first line
		ts.nextValue();
		assertEquals(1, ts.getLinenumber());

		// now next line
		ts.nextValue();
		assertEquals(2, ts.getLinenumber());
	}

	@Test
	public void testLinenumberReadline() {
		String s = "hello world\nnext tokens\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		ts.readLine();

		// still on first line
		assertEquals(1, ts.getLinenumber());

		// now next line
		ts.nextValue();
		assertEquals(2, ts.getLinenumber());
	}

	/**
	 * This is a misfeature of skipSpace, but relied on everywhere.
	 */
	@Test
	public void testSkipOfComments() {
		String s = "hello # some comment\nnext word\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));

		assertEquals("hello", ts.nextValue());
		assertEquals("next", ts.nextValue());
	}

	/**
	 * Turning off automatic comment skipping.
	 */
	@Test
	public void testNoSkipOfComments() {
		String s = "hello # some comment\nnext word\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		ts.setCommentChar(null);

		assertEquals("hello", ts.nextValue());
		assertEquals("#", ts.nextValue());
	}

	@Test
	public void testNextWithNoEOL() {
		String s = "hello";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		Token token = ts.nextToken();
		assertEquals("hello", token.getValue());
	}

	@Test
	public void testReadLineWithNoEOL() {
		String s = "hello";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		String line = ts.readLine();
		assertEquals("hello", line);
	}

	@Test
	public void testReadLineWithCR() {
		String s = "hello\rworld\r";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		String line = ts.readLine();
		assertEquals("hello", line);
		line = ts.readLine();
		assertEquals("world", line);
	}

	@Test
	public void testReadLineReturnsEmptyIfNothing() {
		String s = "";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		String line = ts.readLine();
		assertEquals("", line);
	}

	@Test
	public void testCRLFIsOneLineEnding() {
		String s = "fred\r\n";
		TokenScanner ts = new TokenScanner("", new StringReader(s));
		Token token = ts.nextRawToken();
		assertEquals(TokType.TEXT, token.getType());
		token = ts.nextRawToken();
		assertEquals(TokType.EOL, token.getType());
		token = ts.nextRawToken();
		assertEquals(TokType.EOF, token.getType());
	}
}
