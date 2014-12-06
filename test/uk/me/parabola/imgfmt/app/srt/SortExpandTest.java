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
package uk.me.parabola.imgfmt.app.srt;

import java.io.Reader;
import java.io.StringReader;
import java.text.Collator;

import uk.me.parabola.mkgmap.srt.SrtTextReader;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for characters that are expanded into two or more sort
 * positions.
 */
public class SortExpandTest {
	private Sort sort;
	private Collator collator;

	@Before
	public void setUp() throws Exception {
		Reader r = new StringReader("codepage 1252\n" +
				"code 01\n" +
				"code a, A; â, Â < b, B\n" +
				"code c < d < e <f < g < h < i < j < k < l < m < n < o\n" +
				"code p < q < r,R < s,S < t,T < u < v < w < x < y < z\n" +
				"expand ß to s s\n");
		SrtTextReader srr = new SrtTextReader(r);
		sort = srr.getSort();
		collator = sort.getCollator();
	}

	@Test
	public void testNormal() {
		checkOrder("asßst", "astst");
		checkOrder("asrst", "asßst");
	}

	/**
	 * Expanded letters should sort equal to what they expand to.
	 */
	@Test
	public void testAgainstExpansion() {
		assertEquals(0, compareKey("asssst", "asßst"));
	}

	@Test
	public void testExpandSize() {
		// make sure buffer doesn't overflow when all characters are expanded.
		assertEquals(0, compareKey("……………………", "……………………"));
	}

	private int compareKey(String s1, String s2) {
		SortKey<Object> key1 = sort.createSortKey(null, s1);
		SortKey<Object> key2 = sort.createSortKey(null, s2);
		return key1.compareTo(key2);
	}

	@Test
	public void testGreaterThanInExpansion() {
		checkOrder("aßzaa", "astb");
	}

	@Test
	public void testLessThanInExpansion() {
		checkOrder("asrb", "aßaaa");
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
		assertEquals(-1, k1.compareTo(k2));

		assertEquals(-1, collator.compare(s, s1));
		assertEquals(1, collator.compare(s1, s));
	}
}
