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

package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.scan.SyntaxException;

import org.junit.Test;

import static org.junit.Assert.*;

public class PartFilterTest {
	
	/**
	 *  * Examples from docu: if the value is "Aa#Bb#Cc#Dd#Ee"
 * part:#:1  returns Aa
 * part:#:-1 returns Ee
 * part:#:2  returns Bb
 * part:#:-2 returns Dd
 * part:#>1  returns Bb#Cc#Dd#Ee#
 * part:#<5  returns Aa#Bb#Cc#Dd#
 * part:#<-1 returns Aa#Bb#Cc#Dd#

	 */
	@Test
	public void testNoArg() {
		PartFilter filter = new PartFilter("");
		String s = filter.doFilter("x;y;z", null);
		assertEquals("x", s);
	}
	@Test
	public void testOneArg() {
		PartFilter filter = new PartFilter(";");
		String s = filter.doFilter("x;y;z", null);
		assertEquals("x", s);
	}
	@Test
	public void test2ndArg() {
		PartFilter filter = new PartFilter(":3");
		String s = filter.doFilter("Aa;Bb;Cc;Dd;Ee", null);
		assertEquals("Cc", s);
	}
	@Test
	public void testFirstPart() {
		PartFilter filter = new PartFilter("#:1");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Aa", s);
	}
	@Test
	public void testLastPart() {
		PartFilter filter = new PartFilter("#:-1");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Ee", s);
	}

	@Test
	public void test2ndPart() {
		PartFilter filter = new PartFilter("#:2");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Bb", s);
	}

	@Test
	public void test2ndLastPart() {
		PartFilter filter = new PartFilter("#:-2");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Dd", s);
	}
	@Test
	public void testRestAfter1() {
		PartFilter filter = new PartFilter("#>1");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Bb#Cc#Dd#Ee#", s);
	}
	@Test
	public void testBeforeLast() {
		PartFilter filter = new PartFilter("#<-1");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Aa#Bb#Cc#Dd#", s);
	}
	@Test
	public void testBeforeFifth() {
		PartFilter filter = new PartFilter("#<5");
		String s = filter.doFilter("Aa#Bb#Cc#Dd#Ee", null);
		assertEquals("Aa#Bb#Cc#Dd#", s);
	}
	@Test
	public void testStringContainsNoSeparatorPart1() {
		PartFilter filter = new PartFilter("#:1");
		String s = filter.doFilter("xyz", null);
		assertEquals("xyz", s);
	}
	@Test
	public void testStringContainsNoSeparatorLastPart() {
		PartFilter filter = new PartFilter("#:-1");
		String s = filter.doFilter("xyz", null);
		assertEquals("xyz", s);
	}
	@Test
	public void testStringContainsNoSeparator2ndPart() {
		PartFilter filter = new PartFilter("#:2");
		String s = filter.doFilter("xyz", null);
		assertEquals(null, s);
	}
	@Test
	public void testStringContainsNoSeparator2ndLastPart() {
		PartFilter filter = new PartFilter("#:2");
		String s = filter.doFilter("xyz", null);
		assertEquals(null, s);
	}
	
	
	@Test(expected=SyntaxException.class)
	public void testBadArgNotNum() {
		PartFilter filter = new PartFilter("#<-x");
		filter.doFilter("abc", null);
	}
	@Test(expected=SyntaxException.class)
	public void testBadArgBefore0() {
		PartFilter filter = new PartFilter("#<-0");
		filter.doFilter("abc", null);
	}
	@Test(expected=SyntaxException.class)
	public void testBadArgAfter0() {
		PartFilter filter = new PartFilter("#>-0");
		filter.doFilter("abc", null);
	}
	@Test
	public void testLong1stArg() {
		PartFilter filter = new PartFilter("--->1");
		String s = filter.doFilter("abc---def---ghi", null);
		assertEquals("def---ghi---", s);
	}
//	@Test (expected=SyntaxException.class)
//	public void test1stIsColonArg() {
//		PartFilter filter = new PartFilter("::2");
//		filter.doFilter("abc", null);	}
}
