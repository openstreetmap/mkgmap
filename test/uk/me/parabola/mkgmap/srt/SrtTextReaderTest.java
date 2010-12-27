/*
 * Copyright (C) 2010.
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

package uk.me.parabola.mkgmap.srt;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import uk.me.parabola.imgfmt.app.srt.Sort;

import org.junit.Test;

import static org.junit.Assert.*;

public class SrtTextReaderTest {

	private static final String BASE = "# comment\n" +
			"\n" +
			"codepage 1252\n" +
			"code 01, 02, 03\n";

	/**
	 * Test for a simple case of two letters that have the same major and minor
	 * sort codes.
	 */
	@Test
	public void testSimple() throws Exception {
		char[] sortcodes = getSortcodes("code a, A\n");

		assertEquals("major code", 1, major(sortcodes['a']));
		assertEquals("major code", 1, major(sortcodes['A']));
		assertEquals("minor code", 1, minor(sortcodes['a']));
		assertEquals("minor code", 1, minor(sortcodes['A']));
		assertEquals("subminor code", 1, subminor(sortcodes['a']));
		assertEquals("subminor code", 2, subminor(sortcodes['A']));
	}

	@Test
	public void testCodePage() throws Exception {
		String s = "codepage 1252\n";
		SrtTextReader sr = new SrtTextReader(new CharArrayReader(s.toCharArray()));
		assertEquals(1252, sr.getCodepage());
	}

	@Test
	public void testDescription() throws Exception {
		String val = "Euro Sort";
		String s = String.format("codepage 1252\n" +
				"description '%s'\n", val);
		SrtTextReader sr = new SrtTextReader(new CharArrayReader(s.toCharArray()));
		assertEquals(val, sr.getDescription());
	}

	@Test
	public void testMinorCodes() throws Exception {
		char[] sortcodes = getSortcodes("code a;b;c\n");

		assertEquals("first", 1, minor(sortcodes['a']));
		assertEquals("second", 2, minor(sortcodes['b']));
		assertEquals("third", 3, minor(sortcodes['c']));
	}

	@Test
	public void testSpecifyMajorPos() throws Exception {
		char[] sortcodes = getSortcodes("code pos=0x98 a;b");
		assertEquals(0x98, major(sortcodes['a']));
		assertEquals(0x98, major(sortcodes['b']));
	}

	/**
	 * Letters can be specified by two character hex string.
	 * In this case the hex string is the character in the given codepage.
	 */
	@Test
	public void testHexLetters() throws Exception {
		char[] sortcodes = getSortcodes("code c4;c6");
		assertEquals(1, major(sortcodes[0xc4]));
		assertEquals(0, major(sortcodes[0xc5]));
		assertEquals(1, major(sortcodes[0xc6]));

	}

	@Test
	public void testPunct() throws Exception {
		char[] sortcodes = getSortcodes("code !");
		assertEquals(1, major(sortcodes['!']));
	}

	/**
	 * Check that 88 is not a letter in 1252.
	 * @throws Exception
	 */
	@Test
	public void testNotLetter() throws Exception {
		Sort sort = getSort("code 88");
		byte[] flags = sort.getFlags();

		assertEquals(0, flags[0x88]);
	}

	@Test
	public void testShortCodeSeparator() throws Exception {
		char[] sortcodes = getSortcodes("code a < b < c");
		assertEquals(1, major(sortcodes['a']));
		assertEquals(2, major(sortcodes['b']));
		assertEquals(3, major(sortcodes['c']));
	}

	@Test
	public void testTab2() throws Exception {
		Sort sort = getSort("tab2 12ab");
		List<Character> tab2 = sort.getTab2();
		assertEquals(1, tab2.size());
		assertEquals((char) 0x12ab, (char) tab2.get(0));
	}

	private char[] getSortcodes(String text) throws IOException {
		Sort sort = getSort(text);
		return sort.getSortPositions();
	}

	private Sort getSort(String text) throws IOException {
		String s = BASE + text + "\n";

		Reader r = new StringReader(s);

		SrtTextReader srr = new SrtTextReader(r);
		return srr.getSortcodes();
	}

	private int major(int code) {
		return (code >> 8) & 0xff;
	}

	private int minor(int code) {
		return (code >> 4) & 0xf;
	}
	private int subminor(int code) {
		return (code & 0xf);
	}
}
