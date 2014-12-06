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

import java.util.Arrays;
import java.util.List;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.SyntaxException;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConvertFilterTest {
	private final List<Data> simpleTests = Arrays.asList(
			new Data("kmh=>mph", "100", "62"),
			new Data("km/h=>mph", "100", "62"),
			new Data("mph=>km/h", "60", "97"),
			new Data("m=>ft", "10", "33"),
			new Data("km=>ft", "10", "32808"),
			new Data("ft=>m", "100", "30"),
			new Data("mi=>km", "100", "161"),
			new Data("knots=>mph", "20", "23")
	);

	/** This is not used by this filter, so no need to create a new one for each test */
	private final Element el = new Way(1);

	/**
	 * Just test a whole bunch of different conversions.
	 */
	@Test
	public void testConversions() {
		for (Data data : simpleTests) {
			ConvertFilter f = new ConvertFilter(data.conv);
			String result = f.doFilter(data.input, el);
			assertEquals("Simple test for conversion " + data.conv, data.output, result);
		}
	}

	/**
	 * If there is a unit on the input value, and that is the same as the default, then the conversion
	 * should be between the units as stated.
	 *
	 * Separate test, since there is likely to be a different code path involved.
	 */
	@Test
	public void testConvertWithUnitSameAsDefault() {
		ConvertFilter f = new ConvertFilter("m=>ft");
		assertEquals("328", f.doFilter("100m", el));
	}

	/**
	 * If the value has a unit which is the same as the target unit, then the result will be the
	 * input value (without the unit).
	 */
	@Test
	public void testConvertWIthUnitSameAsTarget() {
		ConvertFilter f = new ConvertFilter("m=>ft");
		assertEquals("100", f.doFilter("100ft", el));
	}

	/**
	 * Test the case where the input string has a unit specified that is neither the source nor the
	 * target string in the conversion specifier.
	 */
	@Test
	public void testConvertWithDifferentUnit() {
		ConvertFilter f = new ConvertFilter("km=>ft");
		assertEquals("33", f.doFilter("10m", el));
	}

	@Test
	public void testConvertNumberWithSpaces() {
		ConvertFilter f = new ConvertFilter("m=>ft");
		String s = f.doFilter(" 10 ", el);
		assertEquals("33", s);
	}

	@Test
	public void testConvertWithSpaces() {
		ConvertFilter f = new ConvertFilter("km/h=>mph");
		String s = f.doFilter(" 10 km/h ", el);
		assertEquals("6", s);
	}

	@Test(expected = SyntaxException.class)
	public void testUnrecognisable() {
		ConvertFilter f = new ConvertFilter("fjdkfjdk");
	}

	@Test
	public void testBadConversion() {
		ConvertFilter f = new ConvertFilter("kk=>ft");

		String in = "10m";
		assertEquals(in, f.doFilter(in, el));
	}

	@Test
	public void testValueNotNumber() {
		ConvertFilter f = new ConvertFilter("km=>m");

		String in = "x10m";
		assertEquals(in, f.doFilter(in, el));
	}

	@Test
	public void testUnknownUnit() {
		ConvertFilter f = new ConvertFilter("m=>ft");
		String in = "10abc";
		String s = f.doFilter(in, el);
		assertEquals(in, s);
	}

	/**
	 * Converting between a distance and a speed for example.
	 */
	@Test
	public void testIncompatibleConversion() {
		ConvertFilter f = new ConvertFilter("m=>mph");
		String in = "10m";
		String s = f.doFilter(in, el);
		assertEquals(in, s);
	}

	class Data {
		private final String conv;
		private final String input;
		private final String output;

		Data(String conv, String input, String output) {
			this.conv = conv;
			this.input = input;
			this.output = output;
		}
	}
}
