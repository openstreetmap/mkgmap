/*
 * Copyright (C) 2014
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
/* Create date: 28-Nov-2014 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 
 * @author GerdP
 *
 */
public class CountryISOFilterTest {
	/**
	 * Test different inputs for the country-ISO filter
	 */
	@Test
	public void testDoFilter() {
		CountryISOFilter filter = new CountryISOFilter();
		String s;
		s = filter.doFilter("Germany", null);
		assertEquals("Germany", "DEU", s);
		s = filter.doFilter("Deutschland", null);
		assertEquals("Deutschland", "DEU", s);
		s = filter.doFilter("United Kingdom", null);
		assertEquals("United Kingdom", "GBR", s);
		s = filter.doFilter("UNITED KINGDOM", null);
		assertEquals("UNITED KINGDOM", "GBR", s);
		s = filter.doFilter("united kingdom", null);
		assertEquals("united kingdom", "GBR", s);
		s = filter.doFilter("UK", null);
		assertEquals("UK", "GBR", s);
		s = filter.doFilter("xyz", null);
		assertEquals("xyz", "xyz", s);
		s = filter.doFilter("Ελλάδα", null);
		assertEquals("Ελλάδα", "GRC", s);
		s = filter.doFilter("  germany ", null);
		assertEquals("  germany ", "DEU", s);

	
	}

}
