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
package uk.me.parabola.imgfmt.app.net;

import java.util.List;

import uk.me.parabola.imgfmt.app.BitReader;

import func.lib.NumberReader;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * There are multiple ways of representing the same set of numbers. So these tests will employ a number
 * reader to parse the resulting bit stream and create a list of numberings that can be compared with
 * the input ones.
 */
public class NumberPreparerTest {
	@Before
	public void setUp() {
	}

	@Test
	public void testNumberConstructor() {
		// A simple test with all numbers increasing.
		String spec = "0,O,1,7,E,2,12";
		Numbering n = new Numbering(spec);

		assertEquals(spec, n.toString());
	}

	/**
	 * Just test that the test infrastructure is working with a known byte stream, this
	 * is testing the tests.
	 */
	@Test
	public void testKnownStream() {
		byte[] buf = {0x41, 0x13, 0x27, 0x49, 0x60};
		BitReader br = new BitReader(buf);
		NumberReader nr = new NumberReader(br);
		List<Numbering> numberings = nr.readNumbers(true);

		assertEquals(1, numberings.size());
		assertEquals("0,E,24,8,O,23,13", numberings.get(0).toString());
	}

}
