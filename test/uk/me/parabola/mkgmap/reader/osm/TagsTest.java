/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 09-Dec-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Arrays;

import static org.junit.Assert.*;
import org.junit.Test;


public class TagsTest {
	private static final String[][] SMALL_SET = {
			{"highway", "primary"},
			{"a", "1"},
			{"b", "2"},
			{"c", "3"},
			{"d", "4"},
	};

	private static final String[][] LARGE_SET = {
			{"jl1", "99"}, {"jl2", "99"}, {"jl3", "99"}, {"jl4", "99"},
			{"kl1", "99"}, {"kl2", "99"}, {"kl3", "99"}, {"kl4", "99"},
			{"ll1", "99"}, {"ll2", "99"}, {"ll3", "99"}, {"ll4", "99"},
			{"ml1", "99"}, {"ml2", "99"}, {"ml3", "99"}, {"ml4", "99"},
			{"nl1", "99"}, {"nl2", "99"}, {"nl3", "99"}, {"nl4", "99"},
			{"jL1", "99"}, {"jL2", "99"}, {"jL3", "99"}, {"jL4", "99"},
			{"kL1", "99"}, {"kL2", "99"}, {"kL3", "99"}, {"kL4", "99"},
			{"LL1", "99"}, {"LL2", "99"}, {"LL3", "99"}, {"LL4", "99"},
			{"mL1", "99"}, {"mL2", "99"}, {"mL3", "99"}, {"mL4", "99"},
			{"nL1", "99"}, {"nL2", "99"}, {"nL3", "99"}, {"nL4", "99"},
	};

	/**
	 * Not needing a resize.
	 */
	@Test
	public void testSmallSet() {
		Tags tags = new Tags();
		for (String[] ss : SMALL_SET) {
			tags.put(ss[0], ss[1]);
		}

		for (String[] ss : SMALL_SET) {
			assertEquals(ss[1], tags.get(ss[0]));
		}
	}

	/**
	 * Larger than the initial size, and so will need to be resized.
	 */
	@Test
	public void testLargeSet() {
		Tags tags = new Tags();
		for (String[] ss : LARGE_SET) {
			tags.put(ss[0], ss[1]);
		}

		for (String[] ss : LARGE_SET) {
			assertEquals(ss[1], tags.get(ss[0]));
		}
	}

	/**
	 * Test removing tags.
	 */
	@Test
	public void testRemove() {
		Tags tags = new Tags();
		for (String[] ss : LARGE_SET)
			tags.put(ss[0], ss[1]);

		for (String[] ss : SMALL_SET)
			tags.put(ss[0], ss[1]);

		String[] toRemove = {"highway", "jl1", "d", "ml3", "nl1", "nL4",
			"kl2", "kl3", "kl4", "kl5"};
		for (String s : toRemove)
			tags.remove(s);

		for (String[] ss : LARGE_SET) {
			if (Arrays.asList(toRemove).contains(ss[0]))
				assertNull(tags.get(ss[0]));
			else
				assertEquals("find for key " + ss[0], ss[1], tags.get(ss[0]));
		}
	}
}
