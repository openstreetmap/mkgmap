/*
 * Copyright (C) 2014.
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

import java.text.Collator;

import uk.me.parabola.mkgmap.srt.SrtTextReader;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class UnicodeKeyTest {

	private Sort sort;

	@Before
	public void setUp() throws Exception {
		sort = SrtTextReader.sortForCodepage(65001);

		Collator collator = sort.getCollator();
		collator.setStrength(Collator.TERTIARY);
	}

	@Test
	public void testUnicodePresent() {
		String description = sort.getDescription();
		assertTrue(description.contains("Unicode"));
		assertFalse(description.contains("Default"));
	}

	@Test
	public void testEquals() {
		String abc = "ABC\u0234\u1023";
		SortKey<Object> key1 = sort.createSortKey(null, abc);
		SortKey<Object> key2 = sort.createSortKey(null, abc);

		assertEquals(0, key1.compareTo(key2));
	}

	@Test
	public void testSimpleLessThan() {
		assertEquals(-1, keyCompare("G", "Ò"));
		assertEquals(-1, keyCompare("G", "Γ"));
	}

	@Test
	public void testExpand() {
		assertEquals(-1, keyCompare("!", "ß"));
		assertEquals(-1, keyCompare("A:", "Ǣ"));
	}

	private int keyCompare(String s1, String s2) {
		SortKey<Object> k1 = sort.createSortKey(null, s1);
		SortKey<Object> k2 = sort.createSortKey(null, s2);
		System.out.println("K1: " + k1);
		System.out.println("K2: " + k2);

		return k1.compareTo(k2);
	}
}
