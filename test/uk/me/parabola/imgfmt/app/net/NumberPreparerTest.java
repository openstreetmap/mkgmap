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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BitReader;
import uk.me.parabola.imgfmt.app.BitWriter;

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
		Numbers n = new Numbers(spec);

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
		List<Numbers> numbers = nr.readNumbers(true);

		assertEquals(1, numbers.size());
		assertEquals("0,E,24,8,O,23,13", numbers.get(0).toString());
	}

	/**
	 * Simple test of numbers that increase on both sides.
	 */
	@Test
	public void testIncreasingNumbers() {
		List<Numbers> numbers = createList(new String[]{"0,O,1,11,E,2,12"});

		List<Numbers> output = writeAndRead(numbers);

		assertEquals(numbers, output);
	}

	@Test
	public void testIncreasingHighStarts() {
		String[] tests = {
				"0,O,1,5,E,2,6",
				"0,O,3,7,E,4,8",
				"0,O,7,7,E,8,8",
				"0,O,91,99,E,92,98",
				"0,O,1,15,E,4,8",
		};

		for (String t : tests) {
			List<Numbers> numbers = createList(new String[]{t});
			List<Numbers> output = writeAndRead(numbers);
			assertEquals(numbers, output);
		}
	}

	private List<Numbers> writeAndRead(List<Numbers> numbers) {
		NumberPreparer preparer = new NumberPreparer(numbers);
		BitWriter bw = preparer.fetchBitStream();
		assertTrue(preparer.isValid());

		// Now read it all back in again
		byte[] bytes = bw.getBytes();
		BitReader br = new BitReader(bytes);
		NumberReader nr = new NumberReader(br);
		return nr.readNumbers(false);
	}

	private List<Numbers> createList(String[] specs) {
		List<Numbers> numbers = new ArrayList<Numbers>();
		for (String s : specs) {
			Numbers n = new Numbers(s);
			numbers.add(n);
		}
		return numbers;
	}
}
