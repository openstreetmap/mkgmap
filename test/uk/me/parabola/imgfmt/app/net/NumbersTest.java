/*
 * Copyright (C) 2015.
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

package uk.me.parabola.imgfmt.app.net;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests  to verify that the plausibility checks are working
 */
public class NumbersTest {

	@Test
	public void testOKOddEvenOverlap() {
		String spec = "0,O,1,7,E,2,12";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	@Test
	public void testBothBothNoOverlap() {
		String spec = "0,B,1,7,B,8,15";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	@Test
	public void testBothEvenNoOverlap() {
		String spec = "0,B,1,7,E,8,16";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	@Test
	public void testBothEvenNoOverlapNotEven() {
		String spec = "0,B,1,7,E,8,15";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testOverlapAtStartEnd() {
		String spec = "0,B,1,7,B,7,16";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testBothEvenOverlap() {
		String spec = "0,B,1,7,E,6,16";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testRangeLargeNumbersOK() {
		String spec = "0,B,10012,10024,N,0,0";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	@Test
	public void testRangeLargeNumbersNotOK() {
		String spec = "0,B,10012,1000240,N,0,0";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testNotOK1() {
		String spec = "0,B,10,23,O,15,15";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testNotOK2() {
		String spec = "0,O,15,15,B,10,23";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testSingleNumBothSides() {
		String spec = "0,O,15,15,O,15,15";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	@Test
	public void testSingleNumOneSideEqualStartOrEndOtherSide1() {
		String spec = "0,O,13,15,O,15,15";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testSingleNumOneSideEqualStartOrEndOtherSide2() {
		String spec = "0,O,15,15,O,13,15";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testSingleNumOneSideEqualStartOrEndOtherSide3() {
		String spec = "0,O,15,13,O,15,15";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testSingleNumOneSideEqualStartOrEndOtherSide4() {
		String spec = "0,O,15,15,O,15,13";
		Numbers numbers = new Numbers(spec);
		assertFalse(numbers.isPlausible());
	}
	@Test
	public void testSingleDifferentNumEachSide() {
		String spec = "0,O,15,15,O,13,13";
		Numbers numbers = new Numbers(spec);
		assertTrue(numbers.isPlausible());
	}
	
	@Test
	public void testCountMatchesValid() {
		String spec = "0,O,1,7,E,2,12";
		Numbers numbers = new Numbers(spec);
		assertEquals(1,numbers.countMatches(1));
		assertEquals(0,numbers.countMatches(13));
	}
	@Test
	public void testCountMatchesGap() {
		String spec = "0,B,1,7,B,9,12";
		Numbers numbers = new Numbers(spec);
		assertEquals(1,numbers.countMatches(1));
		assertEquals(1,numbers.countMatches(7));
		assertEquals(0,numbers.countMatches(8));
		assertEquals(0,numbers.countMatches(13));
	}
	
}
