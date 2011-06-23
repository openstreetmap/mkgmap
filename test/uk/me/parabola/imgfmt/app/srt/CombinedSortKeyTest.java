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

import org.junit.Test;

import static org.junit.Assert.*;

public class CombinedSortKeyTest {

	private static final String HELLO1 = "hello1";

	@Test
	public void testGetObject() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 2);

		assertEquals("retrieve original object", HELLO1, ck1.getObject());
	}

	@Test
	public void testCompletelyEqual() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 2);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k1, 2, 2);

		assertEquals(0, ck1.compareTo(ck2));
	}

	@Test
	public void testDifferentKey() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		IntegerSortKey<String> k2 = new IntegerSortKey<String>(HELLO1, 1, 2);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 2);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k2, 2, 2);

		assertEquals(-1, k1.compareTo(k2));

		assertEquals(-1, ck1.compareTo(ck2));
		assertEquals(1, ck2.compareTo(ck1));
	}

	@Test
	public void testDifferentFirst() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		IntegerSortKey<String> k2 = new IntegerSortKey<String>(HELLO1, 1, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 2);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k2, 3, 2);

		assertEquals(0, k1.compareTo(k2));

		assertEquals(-1, ck1.compareTo(ck2));
		assertEquals(1, ck2.compareTo(ck1));
	}

	@Test
	public void testDifferentSecond() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		IntegerSortKey<String> k2 = new IntegerSortKey<String>(HELLO1, 1, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 2);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k2, 2, 3);

		assertEquals(0, k1.compareTo(k2));

		assertEquals(-1, ck1.compareTo(ck2));
		assertEquals(1, ck2.compareTo(ck1));
	}

	@Test
	public void testKeyOverridesFirst() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		IntegerSortKey<String> k2 = new IntegerSortKey<String>(HELLO1, 2, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 3, 2);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k2, 2, 2);

		assertEquals(-1, k1.compareTo(k2));

		assertEquals(-1, ck1.compareTo(ck2));
		assertEquals(1, ck2.compareTo(ck1));
	}

	@Test
	public void testPrimaryOverridesSecond() {
		IntegerSortKey<String> k1 = new IntegerSortKey<String>(HELLO1, 1, 1);
		IntegerSortKey<String> k2 = new IntegerSortKey<String>(HELLO1, 1, 1);
		CombinedSortKey<String> ck1 = new CombinedSortKey<String>(k1, 2, 3);
		CombinedSortKey<String> ck2 = new CombinedSortKey<String>(k2, 3, 2);

		assertEquals(0, k1.compareTo(k2));

		assertEquals(-1, ck1.compareTo(ck2));
		assertEquals(1, ck2.compareTo(ck1));
	}
}
