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
/* Create date: 09-Aug-2009 */
package uk.me.parabola.mkgmap;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import org.junit.Test;

public class OptionsTest {
	private static final String[] STD_SINGLE_OPTS = {
		"pool", "ocean"
	};

	private final List<Option> found = new ArrayList<Option>();
	private final List<String> options = new ArrayList<String>();
	private final List<String> values = new ArrayList<String>();

	/**
	 * You can have options with values separated by either a ':' or an
	 * equals sign.
	 */
	@Test
	public void testOptionsWithValues() {
		String s = "three=3\nfour:4\n";
		readOptionsFromString(s);

		assertEquals("correct number", 2, found.size());

		assertArrayEquals("options", new String[] {
				"three", "four"
		}, options.toArray());

		assertArrayEquals("values", new String[] {
				"3", "4"
		}, values.toArray());
	}

	/**
	 * Options do not need to have a value
	 */
	@Test
	public void testOptionsWithoutValues() {
		String s = "pool\nocean\n";
		readOptionsFromString(s);

		assertEquals("number of options found", 2, found.size());
		assertArrayEquals("options", STD_SINGLE_OPTS, options.toArray());
		checkEmptyValues();
	}

	/**
	 * Comments can appear as the first significant character of a line
	 * and cause the rest of the line to be skipped.
	 */
	@Test
	public void testComments() {
		String s = "pool\n" +
				"    # first comment\n" +
				"# a whole line of comment  \n" +
				"ocean\n";

		readOptionsFromString(s);

		assertEquals("number of options found", 2, found.size());
		assertArrayEquals("options", STD_SINGLE_OPTS, options.toArray());
		checkEmptyValues();
	}

	/**
	 * An option can have a long value that is surrounded by braces. All
	 * leading and trailing white space is trimmed.
	 */
	@Test
	public void testLongValues() {
		final String OPT1 = "This is a much longer value\n" +
				"that spans several\n" +
				"lines\n";
		final String OPT2 = "  and here is another, note that there was no new" +
				"line before the option name.";

		String s = "pool {" + OPT1 + "}" +
				"ocean {\n" + OPT2 + "}";
		readOptionsFromString(s);

		System.out.println(options);
		System.out.println(values);
		assertEquals("number of options found", 2, found.size());
		assertArrayEquals("options", STD_SINGLE_OPTS, options.toArray());
		assertEquals("first value", OPT1.trim(), values.get(0));
		assertEquals("second value", OPT2.trim(), values.get(1));
	}

	private void checkEmptyValues() {
		for (String s : values) {
			assertEquals("value", "", s);
		}
	}

	private void readOptionsFromString(String s) {
		OptionProcessor proc = new MyOptionProcessor();
		Options opts = new Options(proc);
		Reader r = new StringReader(s);

		opts.readOptionFile(r, "from-string");
	}

	private class MyOptionProcessor implements OptionProcessor {
		public void processOption(Option opt) {
			found.add(opt);
			options.add(opt.getOption());
			values.add(opt.getValue());
		}
	}
}
