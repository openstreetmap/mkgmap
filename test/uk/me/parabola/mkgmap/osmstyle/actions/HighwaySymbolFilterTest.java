/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 21-Jul-2009 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Steve Ratcliffe
 */
public class HighwaySymbolFilterTest {
	/**
	 * Basic test for a mostly numeric ref.
	 */
	@Test
	public void testDoFilter() {
		HighwaySymbolFilter filter = new HighwaySymbolFilter("shield");
		String s = filter.doFilter("A101");
		assertEquals("A101", "\u0002A101", s);
	}

	/**
	 * If there is one space, then there should be no spaces in the output.
	 */
	@Test
	public void testOneSpace() {
		HighwaySymbolFilter filter = new HighwaySymbolFilter("shield");
		String s = filter.doFilter("A 101");
		assertEquals("with one space", "\u0002A101", s);
	}

	/**
	 * If there are multiple spaces, then none are removed.
	 * (Not sure why this exists).
	 */
	@Test
	public void testMultipleSpaces() {
		HighwaySymbolFilter filter = new HighwaySymbolFilter("shield");
		String s = filter.doFilter("A 1 01");
		assertEquals("two spaces", "\u0002A 1 01", s);
	}

	/**
	 * Strings that are mostly alphabetic do not get changed.
	 */
	@Test
	public void testMostlyAlpha() {
		HighwaySymbolFilter filter = new HighwaySymbolFilter("shield");
		String value = "AN1";
		String s = filter.doFilter(value);
		assertEquals("mostly alphabetic", value, s);
	}
}
