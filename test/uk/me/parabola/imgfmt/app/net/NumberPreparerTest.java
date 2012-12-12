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
import java.util.Random;

import uk.me.parabola.imgfmt.app.BitReader;
import uk.me.parabola.imgfmt.app.BitWriter;

import func.lib.NumberReader;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * There are multiple ways of representing the same set of numbers. So these tests will employ a number
 * reader to parse the resulting bit stream and create a list of numberings that can be compared with
 * the input ones.
 */
public class NumberPreparerTest {

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
		nr.setNumberOfNodes(1);
		List<Numbers> numbers = nr.readNumbers(true);

		assertEquals(1, numbers.size());
		assertEquals("0,E,24,8,O,23,13", numbers.get(0).toString());
	}

	/**
	 * Simple test of numbers that increase on both sides.
	 */
	@Test
	public void testIncreasingNumbers() {
		run("0,O,1,11,E,2,12");
	}

	@Test
	public void testSwappedDefaultStyles() {
		List<Numbers> numbers = createList(new String[]{"0,E,2,12,O,1,11"});
		List<Numbers> output = writeAndRead(numbers);
		assertEquals(numbers, output);
	}

	@Test
	public void testIncreasingHighStarts() {
		String[] tests = {
				"0,O,1,5,E,2,6",
				"0,O,3,7,E,4,8",
				"0,O,91,99,E,92,98",
				"0,O,1,15,E,4,8",
		};

		for (String t : tests) {
			List<Numbers> numbers = createList(new String[]{t});
			List<Numbers> output = writeAndRead(numbers);
			assertEquals(numbers, output);
		}
	}

	@Test
	public void testSingleNumbers() {
		runSeparate("0,O,7,7,E,8,8", "0,O,7,7,E,6,6");
	}

	@Test
	public void testLargeDifferentStarts() {
		runSeparate("0,O,91,103,E,2,8", "0,E,90,102,O,3,9");
	}

	@Test
	public void testMultipleNodes() {
		List<Numbers> numbers = createList(new String[]{
				"0,O,1,9,E,2,12",
				"1,O,11,17,E,14,20",
				"2,O,21,31,E,26,36",
		});
		List<Numbers> output = writeAndRead(numbers);
		assertEquals(numbers, output);
	}

	@Test
	public void testMultipleWithReverse() {
		run("0,E,2,2,O,1,5", "1,E,2,10,O,5,17");
	}

	@Test
	public void testDecreasing() {
		run("0,O,25,11,E,24,20");
	}

	@Test
	public void testMixedStyles() {
		run("0,O,1,9,E,6,12", "1,E,14,22,O,9,17", "2,O,17,21,E,26,36");
	}

	@Test
	public void testOneSide() {
		runSeparate("0,N,-1,-1,O,9,3");
		runSeparate("0,E,2,8,N,-1,-1", "0,N,-1,-1,O,9,3");
	}

	@Test
	public void testBoth() {
		runSeparate("0,B,1,10,B,11,20");
	}

	@Test
	public void testLargeRunsAndGaps() {
		run("0,E,100,200,O,111,211", "1,E,400,500,O,421,501", "2,E,600,650,O,601,691");
	}

	@Test
	public void testSkip() {
		run("0,E,2,20,O,1,9", "3,O,3,9,E,2,2");
	}

	@Test
	public void testSkipFirst() {
		run("2,O,1,5,E,2,2");
	}

	@Test
	public void testLargeSkip() {
		run("0,N,-1,-1,E,2,4", "100,O,1,9,E,8,16");
	}

	@Test
	public void testRegression() {
		String[][] tests = {
				{"0,N,-1,-1,B,6,5", "1,O,3,11,O,3,3"},
				{"0,O,7,1,O,9,5", "1,O,27,23,O,3,5"},
				{"0,B,5,5,E,12,8"},
		};

		for (String[] sarr : tests)
			run(sarr);
	}

	@Test @Ignore(value = "long running test")
	public void testRandom() {
		Random rand = new Random(8866029);

		for (int iter = 0; iter < 10000000; iter++) {
			List<String> sl = new ArrayList<String>();
			for (int i = 0; i < 5; i++) {
				String n;
				do {
					String r1 = getRange(rand);
					String r2 = getRange(rand);

					n = String.format("%d,%s,%s", i, r1, r2);
				} while (i == 0 && n.contains("N,-1,-1,N"));

				sl.add(n);
				if (rand.nextInt(3) > 1)
					break;
			}

			if ((iter % 500000) == 0)
				System.out.println("Done " + iter);
			//System.out.println(sl);
			run(sl.toArray(new String[sl.size()]));
		}
	}

	private String getRange(Random rand) {
		char style = "NEEEOOOBB".charAt(rand.nextInt(9));
		//if (style == 'N') style = 'B';
		int max = 10;
		int r = rand.nextInt(20);
		if (r > 19) max = 200;
		if (r > 17) max = 30;

		int start = rand.nextInt(max)+1;
		int end = rand.nextInt(max)+1;
		if (style == 'O') {
			start |= 1;
			end |= 1;
		} else if (style == 'E') {
			start++; end++;
			start &= ~1;
			end &= ~1;
		} else if (style == 'N') {
			start = end = -1;
		}
		return String.format("%c,%d,%d", style, start, end);
	}

	// Helper routines
	private void runSeparate(String... numbers) {
		for (String s : numbers)
			run(s);
	}

	private void run(String ... numbers) {
		List<Numbers> nList = createList(numbers);
		List<Numbers> output = writeAndRead(nList);
		assertEquals(nList, output);
	}

	private List<Numbers> writeAndRead(List<Numbers> numbers) {
		NumberPreparer preparer = new NumberPreparer(numbers);
		BitWriter bw = preparer.fetchBitStream();
		assertTrue("check valid flag", preparer.isValid());

		boolean swapped = preparer.getSwapped();

		// Now read it all back in again
		byte[] b1 = bw.getBytes();
		byte[] bytes = new byte[bw.getLength()];
		System.arraycopy(b1, 0, bytes, 0, bw.getLength());

		BitReader br = new BitReader(bytes);
		NumberReader nr = new NumberReader(br);
		nr.setNumberOfNodes(numbers.size());
		List<Numbers> list = nr.readNumbers(swapped);
		for (Numbers n : list) 
			n.setNodeNumber(n.getRnodNumber());

		return list;
	}

	private List<Numbers> createList(String[] specs) {
		List<Numbers> numbers = new ArrayList<Numbers>();
		for (String s : specs) {
			Numbers n = new Numbers(s);
			n.setRnodNumber(n.getNodeNumber());
			numbers.add(n);
		}
		return numbers;
	}
}
