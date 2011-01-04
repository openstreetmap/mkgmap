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

import uk.me.parabola.mkgmap.srt.SrtTextReader;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SortTest {
	private Sort sort;

	@Before
	public void setUp() throws Exception {
		Reader r = new StringReader("codepage 1252\n" +
				"code 01\n" +
				"code a, A; â Â < b, B;\n");
		SrtTextReader srr = new SrtTextReader(r);
		sort = srr.getSort();
	}

	@Test
	public void testSame() throws Exception {
		String s = "aAbâ";
		SortKey<Object> k1 = sort.createSortKey(null, s, 0);
		SortKey<Object> k2 = sort.createSortKey(null, s, 0);

		assertEquals(0, k1.compareTo(k2));
	}

	@Test
	public void testDifferentLengths() throws Exception {
		SortKey<Object> k1 = sort.createSortKey(null, "aabb", 0);
		SortKey<Object> k2 = sort.createSortKey(null, "aab", 0);

		assertEquals(1, k1.compareTo(k2));
		assertEquals(-1, k2.compareTo(k1));
	}

	@Test
	public void testPrimaryDifference() throws Exception {
		checkOrder("AAA", "AAB");
	}

	@Test
	public void testSecondaryDifferences() throws Exception {
		checkOrder("AAA", "AÂA");
	}

	@Test
	public void testTertiaryDifferences() throws Exception {
		checkOrder("AAa", "AAA");
	}

	@Test
	public void testPrimaryOverridesSecondary() throws Exception {
		checkOrder("AAAA", "ÂAAA");
		checkOrder("ÂAAA", "AAAB");
	}

	@Test
	public void testSecondaryOverridesTertiary() throws Exception {
		checkOrder("aaa", "Aaa");
		checkOrder("Aaa", "aâa");
		checkOrder("Aaa", "aÂa");
	}

	@Test
	public void testSecondarySort() {
		checkOrder(1, 24);
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
		SortKey<Object> k1 = sort.createSortKey(null, s, 0);
		SortKey<Object> k2 = sort.createSortKey(null, s1, 0);

		assertEquals(1, k2.compareTo(k1));
	}
}
