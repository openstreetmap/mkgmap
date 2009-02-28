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
import java.util.Iterator;

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
		Tags tags = smallSetTags();

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

	/**
	 * Test that we can add tags during the iteration and that they will
	 * appear at the end of the iteration.  This is behaviour that is not
	 * usually expected of iterators.
	 */
	@Test
	public void testAddTagDuringIter() {
		Tags tags = smallSetTags();
		Iterator<String> it = iterateOverTags(tags);

		// Add an extra tag.  We are already at the end of the iteration and so
		// this should force into play the 
		tags.put("added", "iter");
		assertTrue("more to iterate over", it.hasNext());

		// The addition should cause two more values to be returned.  The
		// first is the specific value.
		assertTrue(it.hasNext());
		assertEquals("the specific tag added", "added=iter", it.next());

		// The second is the wildcard value.
		assertTrue(it.hasNext());
		assertEquals("the wildcard tag added", "added=*", it.next());

		// And that should be it...
		assertNull("iteration is finished", it.hasNext());
	}

	@Test
	public void testAddMultipleTagsDuringIter() {
		Tags tags = smallSetTags();
		Iterator<String> it = iterateOverTags(tags);

		String[][] addlist = {
				{"add1", "first"},
				{"add2", "second"},
				{"add3", "third"},
				{"add4", "fourth"},
		};
		for (String[] a : addlist)
			tags.put(a[0], a[1]);

		for (String[] a : addlist) {
			assertTrue(it.hasNext());
			assertEquals("tag added", a[0] + '=' + a[1], it.next());
			assertEquals("tag added wildcard", a[0] + "=*", it.next());
		}

		assertFalse("iteration finished", it.hasNext());
	}

	/**
	 * Test that tags that are added during an iteration run, are kept and are
	 * seen on subsequent iterations.
	 */
	@Test
	public void testAddedTagsKeptAfterIter() {
		Tags tags = smallSetTags();

		iterateOverTags(tags);

		tags.put("added", "iter");

		// Check for the extra tags
		Iterator<String> it = tags.iterator();
		int n = (SMALL_SET.length + 1) * 2;
		for (int i = 0; i < n; i++) {
			assertTrue("has next at position "+i, it.hasNext());
			assertNotNull("result should be non null", it.next());
		}
	}

	/**
	 * Test that tags that are added during an iteration run, are kept after
	 * the object is copied with {@link Tags#copy}.
	 */
	@Test
	public void testAddedTagsKeptOnCopy() {
		Tags tags = smallSetTags();

		iterateOverTags(tags);

		tags.put("added", "iter");

		// Copy the tags.
		tags = tags.copy();

		// Check that the copy contains the extra tags.
		Iterator<String> it = tags.iterator();
		int n = (SMALL_SET.length + 1) * 2;
		for (int i = 0; i < n; i++) {
			assertTrue("has next at position "+i, it.hasNext());
			assertNotNull("result should be non null", it.next());
		}
	}

	/**
	 * Create tags initialised with SMALL_SET.
	 */
	private Tags smallSetTags() {
		Tags tags = new Tags();
		for (String[] ts : SMALL_SET)
			tags.put(ts[0], ts[1]);
		return tags;
	}

	/**
	 * Create an iterator over the tags.  This must be initialised to the
	 * values in SMALL_SET.
	 * @param tags The tags containing values from SMALL_SET.
	 * @return An iterator that has iterated over all the tags in set.
	 */
	private Iterator<String> iterateOverTags(Tags tags) {
		Iterator<String> it = tags.iterator();
		int n = SMALL_SET.length * 2;
		for (int i = 0; i < n; i++) {
			assertTrue(it.hasNext());
			assertNotNull("result should be non null", it.next());
		}
		return it;
	}


}
