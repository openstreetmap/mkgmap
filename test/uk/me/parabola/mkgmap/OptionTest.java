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
/* Create date: 08-Aug-2009 */
package uk.me.parabola.mkgmap;

import org.junit.Test;

import static org.junit.Assert.*;


public class OptionTest {
	/** If an option does not have a value, then the value is the empty
	 * string.
	 */
	@Test
	public void testOptionWithoutValue() {
		Option o = new Option("hello");
		assertEquals("name", "hello", o.getOption());
		assertEquals("value", "", o.getValue());
	}


	@Test
	public void testOption() {
		Option o = new Option("hello", "world");
		assertEquals("name", "hello", o.getOption());
		assertEquals("value", "world", o.getValue());
		assertFalse("not experimental", o.isExperimental());
	}

	/**
	 * Regular option, parsed in constructor.
	 */
	@Test
	public void testParseOption() {
		Option o = new Option("hello=world");
		assertEquals("name", "hello", o.getOption());
		assertEquals("value", "world", o.getValue());
		assertFalse("not experimental", o.isExperimental());
	}

	/**
	 * Test for an experimental option.  These begin with 'x-' but are otherwise
	 * treated as if the 'x-' was not there.
	 */
	@Test
	public void testIsExperimental() {
		Option o = new Option("x-hello=world");
		assertEquals("name", "hello", o.getOption());
		assertEquals("value", "world", o.getValue());
		assertTrue("experimental", o.isExperimental());
	}
}
