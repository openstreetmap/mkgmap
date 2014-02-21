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
				"code =0001=007f\n" +
				"< a, ª, A; â, Â < b, B < e,E < m,M < t,T\n" +
				"expand ™ to T M\n" +
				"expand æ to a e\n" +
				"expand Æ to A E\n");
		sort = SrtTextReader.sortForCodepage(1252);
		//sort = srr.getSort();
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
		checkOrdered("AAA", "AAB");
	}

	@Test
	public void testSecondaryDifferences() {
		checkOrdered("AAA", "AÂA");
	}

	@Test
	public void testTertiaryDifferences() {
		checkOrdered("AAa", "AAA");
	}

	@Test
	public void testPrimaryOverridesSecondary() {
		checkOrdered("AAAA", "ÂAAA");
		checkOrdered("ÂAAA", "AAAB");
	}

	@Test
	public void testSecondaryOverridesTertiary() {
		checkOrdered("aaa", "Aaa");
		checkOrdered("Aaa", "aâa");
		checkOrdered("Aaa", "aÂa");
	}

	@Test
	public void testSecondarySort() {
		checkOrdered(1, 24);
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
	public void testTertiaryPlusExpansion() {
		assertEquals(-1, keyCompare("æ", "ªe"));
		assertEquals(-1, keyCompare("`æ", "`ªe"));
	}

	/**
	 * Make the internal initial buffer overflow so it has to be reallocated.
	 */
	@Test
	public void testKeyOverflow() {
		assertEquals(1, keyCompare("™™™™™", "AA"));
	}

	@Test
	public void testExpanded() {
		assertEquals(-1, keyCompare("æ", "Ae"));
		assertEquals(-1, keyCompare("æ", "AE"));
		assertEquals(0, keyCompare("æ", "ae"));
		assertEquals(-1, keyCompare("æ", "aE"));
		assertEquals(1, keyCompare("AE", "aE"));
		assertEquals(1, keyCompare("Æ", "aE"));
	}

	@Test
	public void testExpand2() {
		assertEquals(1, keyCompare("™ð", "tMÐ"));
	}

	@Test
	public void testExpandedAndIgnorable() {
		assertEquals(0, keyCompare("æ", "ae"));
		assertEquals(-1, keyCompare("\u007fæ", "Ae"));
	}

	@Test
	public void testIgnorableCharacters() {
		assertEquals(0, keyCompare("aaa", "a\u0008aa"));

		assertEquals(-1, keyCompare("\u007f", "(T"));
	}

	private int keyCompare(String s1, String s2) {
		SortKey<Object> k1 = sort.createSortKey(null, s1);
		SortKey<Object> k2 = sort.createSortKey(null, s2);
		System.out.println("K1: " + k1);
		System.out.println("K2: " + k2);

		return k1.compareTo(k2);
	}

	private void checkOrdered(int i1, int i2) {
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
	private void checkOrdered(String s, String s1) {
		SortKey<Object> k1 = sort.createSortKey(null, s);
		SortKey<Object> k2 = sort.createSortKey(null, s1);

		assertEquals(1, k2.compareTo(k1));
		assertEquals(-1, k1.compareTo(k2));
		assertEquals(-1, collator.compare(s, s1));
		assertEquals(1, collator.compare(s1, s));
	}
}
