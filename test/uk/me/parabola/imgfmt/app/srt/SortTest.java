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

package uk.me.parabola.imgfmt.app.srt;

import java.io.Reader;
import java.io.StringReader;
import java.text.Collator;

import uk.me.parabola.mkgmap.srt.SrtTextReader;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SortTest {
	private Sort sort;
	private Collator collator;

	@Before
	public void setUp() throws Exception {
		Reader r = new StringReader("codepage 1252\n" +
				"code 01\n" +
				"code a, A; â, Â < b, B;\n");
		SrtTextReader srr = new SrtTextReader(r);
		sort = srr.getSort();
		collator = sort.getCollator();
		collator.setStrength(Collator.TERTIARY);
	}

	@Test
	public void testSame() {
		String s = "aAbâ";
		SortKey<Object> k1 = sort.createSortKey(null, s);
		SortKey<Object> k2 = sort.createSortKey(null, s);

		assertEquals(0, k1.compareTo(k2));
	}

	@Test
	public void testDifferentLengths() {
		SortKey<Object> k1 = sort.createSortKey(null, "aabbbb");
		SortKey<Object> k2 = sort.createSortKey(null, "aab");

		assertEquals(1, k1.compareTo(k2));
		assertEquals(-1, k2.compareTo(k1));
	}

	@Test
	public void testPrimaryDifference() {
		checkOrder("AAA", "AAB");
	}

	@Test
	public void testSecondaryDifferences() {
		checkOrder("AAA", "AÂA");
	}

	@Test
	public void testTertiaryDifferences() {
		checkOrder("AAa", "AAA");
	}

	@Test
	public void testPrimaryOverridesSecondary() {
		checkOrder("AAAA", "ÂAAA");
		checkOrder("ÂAAA", "AAAB");
	}

	@Test
	public void testSecondaryOverridesTertiary() {
		checkOrder("aaa", "Aaa");
		checkOrder("Aaa", "aâa");
		checkOrder("Aaa", "aÂa");
	}

	@Test
	public void testSecondarySort() {
		checkOrder(1, 24);
	}

	/**
	 * Test for a bad character in the input.
	 * Probably want the character to be replaced by a question mark rather
	 * than give an error.
	 * Strings with bad characters should not compare equal to other strings
	 * or throw exceptions.
	 */
	@Test
	public void testBadCharacter() {
		String s = "a\u063ab";
		SortKey<Object> k1 = sort.createSortKey(null, s);
		SortKey<Object> k2 = sort.createSortKey(null, "aa");

		int res = k1.compareTo(k2);
		assertTrue(res != 0);

		res = k2.compareTo(k1);
		assertTrue(res != 0);

		// not equal to an empty string.
		k2 = sort.createSortKey(null, "");
		res = k1.compareTo(k2);
		assertTrue(res != 0);

		// character is replaced with '?'
		k2 = sort.createSortKey(null, "a?b");
		res = k1.compareTo(k2);
		assertEquals(0, res);
	}

	@Test
	public void testCollatorPrimary() {
		Collator collator = sort.getCollator();
		collator.setStrength(Collator.PRIMARY);
		assertEquals(0, collator.compare("aa", "aa"));
		assertEquals(0, collator.compare("aa", "âa"));
		assertEquals(0, collator.compare("Aa", "aA"));
		assertEquals(1, collator.compare("ab", "âa"));

		assertEquals(1, collator.compare("aaa", "aa"));
		assertEquals(-1, collator.compare("aa", "aaa"));
	}

	@Test
	public void testCollatorSecondary() {
		Collator collator = sort.getCollator();
		collator.setStrength(Collator.SECONDARY);
		assertEquals(0, collator.compare("aa", "aa"));
		assertEquals(0, collator.compare("aA", "aa"));
		assertEquals(-1, collator.compare("aa", "âa"));
		assertEquals(0, collator.compare("âa", "âa"));
		assertEquals(1, collator.compare("ab", "âa"));

		assertEquals(1, collator.compare("aaaa", "aaa"));
		assertEquals(-1, collator.compare("aaa", "aaaa"));
	}

	@Test
	public void testCollatorTertiary() {
		Collator collator = sort.getCollator();
		collator.setStrength(Collator.TERTIARY);
		assertEquals(0, collator.compare("aa", "aa"));
		assertEquals(1, collator.compare("aA", "aa"));
		assertEquals(-1, collator.compare("aaa", "âaa"));
		assertEquals(0, collator.compare("âaa", "âaa"));
		assertEquals(1, collator.compare("ab", "âa"));

		assertEquals(1, collator.compare("AAA", "AA"));
		assertEquals(-1, collator.compare("AA", "AAA"));
	}

	@Test
	public void testIgnorableCharacters() {
		checkOrder("aa", "\004aa");
	}

	private void checkOrder(int i1, int i2) {
		String s = "aaa";
		SortKey<Object> k1 = sort.createSortKey(null, s, i1);
		SortKey<Object> k2 = sort.createSortKey(null, s, i2);
		assertEquals(1, k2.compareTo(k1));
	}

	/**
	 * Check and assert that the second string is greater than the first.
	 * @param s First string.
	 * @param s1 Second string.
	 */
	private void checkOrder(String s, String s1) {
		SortKey<Object> k1 = sort.createSortKey(null, s);
		SortKey<Object> k2 = sort.createSortKey(null, s1);

		assertEquals(1, k2.compareTo(k1));
		assertEquals(-1, collator.compare(s, s1));
	}
}
