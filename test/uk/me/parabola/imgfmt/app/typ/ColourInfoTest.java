/*
 * Copyright (C) 2011.
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

package uk.me.parabola.imgfmt.app.typ;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ColourInfoTest {
	private ColourInfo ci;

	@Before
	public void setUp() {
		ci = new ColourInfo();
	}

	/**
	 * One colour, no bitmap, therefore day-only and implied transparent day other.
	 * => 6
	 */
	@Test
	public void testSchemeC1() {
		ci.addColour("a", new Rgb(1,1,1));
		int cs = ci.getColourScheme();
		assertEquals(6, cs);
	}

	/**
	 * Two colours, no bitmap, no border.
	 * day+night, 2 solid, implied transparent other => 7.
	 */
	@Test
	public void testSchemeC2() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addColour("b", new Rgb(1,1,2));
		int cs = ci.getColourScheme();
		assertEquals(7, cs);
	}

	/**
	 * Four colours, second transparent, no border or bitmap.
	 * day with transparent, night 2 solid => 3 (probably not allowed)
	 */
	@Test
	public void testSchemeC4T2() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,2));
		ci.addColour("d", new Rgb(1,1,2));
		int cs = ci.getColourScheme();
		assertEquals(3, cs);
	}

	/**
	 * Four colours, night has transparent, no border or bitmap.
	 * day solid, night transparent => 5 (probably not allowed)
	 */
	@Test
	public void testSchemeC4T4() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addColour("b", new Rgb(1,1,1));
		ci.addColour("c", new Rgb(1,1,2));
		ci.addTransparent("d");
		int cs = ci.getColourScheme();
		assertEquals(5, cs);
	}
	
	/**
	 * Four colours, both have transparent, no border or bitmap.
	 * day transparent, night transparent => 7 (probably not allowed)
	 */
	@Test
	public void testSchemeC4T24() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,1));
		ci.addTransparent("d");
		int cs = ci.getColourScheme();
		assertEquals(7, cs);
	}

	/**
	 * Two colours, image.
	 * day 2 solid => 8
	 */
	@Test
	public void testSchemeC2Img() {
		ci.addColour("a", new Rgb(1, 1, 1));
		ci.addColour("b", new Rgb(1,1,2));
		ci.setHasBitmap(true);
		int cs = ci.getColourScheme();
		assertEquals(8, cs);
	}

	/*
	 * Two colours, second transparent, image.
	 * Day with transparent, image => e
	 */
	@Test
	public void testSchemeC2T2Img() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.setHasBitmap(true);
		int cs = ci.getColourScheme();
		assertEquals(0xe, cs);
	}

	/**
	 * Four colours, day has transparent, image.
	 * day with transparent, night 2 solid => 8+3
	 */
	@Test
	public void testSchemeC4T2Img() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,2));
		ci.addColour("d", new Rgb(1,1,2));
		ci.setHasBitmap(true);
		int cs = ci.getColourScheme();
		assertEquals(0xb, cs);
	}

	/**
	 * Four colours, night has transparent, image.
	 * day solid, night transparent => 8+5
	 */
	@Test
	public void testSchemeC4T4Img() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addColour("b", new Rgb(1,1,1));
		ci.addColour("c", new Rgb(1,1,2));
		ci.addTransparent("d");
		ci.setHasBitmap(true);
		int cs = ci.getColourScheme();
		assertEquals(0xd, cs);
	}

	/**
	 * Four colours, both have transparent, image.
	 * day transparent, night transparent => f
	 */
	@Test
	public void testSchemeC4T24Img() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,1));
		ci.addTransparent("d");
		ci.setHasBitmap(true);
		int cs = ci.getColourScheme();
		assertEquals(0xf, cs);
	}

	/**
	 * One colour, border.
	 * day-only with implied transparent => 6 (but probably not allowed)
	 */
	@Test
	public void testSchemeC1Brd() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.setHasBorder(true);
		int cs = ci.getColourScheme();
		assertEquals(6, cs);
	}

	/**
	 * Two colours, border.
	 * day 2 solid => 0
	 */
	@Test
	public void testSchemeC2Brd() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addColour("b", new Rgb(1,1,2));
		ci.setHasBorder(true);
		int cs = ci.getColourScheme();
		assertEquals(0, cs);
	}

	/**
	 * Four colours, second transparent, border.
	 * day with transparent, night 2 solid => 3 (probably not allowed)
	 */
	@Test
	public void testSchemeC4T2Brd() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,2));
		ci.addColour("d", new Rgb(1,1,2));
		ci.setHasBorder(true);
		int cs = ci.getColourScheme();
		assertEquals(3, cs);
	}

	/**
	 * Four colours, night has transparent, border.
	 * day solid, night transparent => 5 (probably not allowed)
	 */
	@Test
	public void testSchemeC4T4Brd() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addColour("b", new Rgb(1,1,1));
		ci.addColour("c", new Rgb(1,1,2));
		ci.addTransparent("d");
		ci.setHasBorder(true);
		int cs = ci.getColourScheme();
		assertEquals(5, cs);
	}

	/**
	 * Four colours, both have transparent, border.
	 * day transparent, night transparent => 7
	 */
	@Test
	public void testSchemeC4T24Brd() {
		ci.addColour("a", new Rgb(1,1,1));
		ci.addTransparent("b");
		ci.addColour("c", new Rgb(1,1,1));
		ci.addTransparent("d");
		ci.setHasBorder(true);
		int cs = ci.getColourScheme();
		assertEquals(7, cs);
	}

}
