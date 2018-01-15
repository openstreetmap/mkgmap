/*
 * Copyright (C) 2018.
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
package uk.me.parabola.imgfmt.app.dem;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import uk.me.parabola.mkgmap.reader.hgt.HGTReader;

/**
 * Test some inputs for class DemTile.
 * @author Gerd Petermann
 *
 */
public class DemTileTest {
	
	@Test
	public void testKnownBitstream() throws Exception {
		// Example from Dem-Daten.pdf by Frank Stinner
		short[] realHeights = new short[64*64];
		realHeights[63 * 64] = 3;
		DEMTile dt = new DEMTile(0, 0, 64,64, realHeights);
		int bsLen = dt.getBitStreamLen();
		byte[] exprectedRes = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xC0, 0x2e};
		assertEquals(12, bsLen);
		String res = Arrays.toString(dt.getBitStream());
		assertEquals(Arrays.toString(exprectedRes), res); 
	}	
	
	@Test
	public void testAllZero() throws Exception {
		short[] realHeights = new short[64*64];
		DEMTile dt = new DEMTile(0, 0, 64,64, realHeights);
		int bsLen = dt.getBitStreamLen();
		assertEquals(0, bsLen);
		assertEquals(0, dt.getBaseHeight());
		assertEquals(0, dt.getMaxDeltaHeight());
		assertEquals(0, dt.getEncodingType());
	}	
	
	@Test
	public void testAllOne() throws Exception {
		short[] realHeights = new short[64*64];
		Arrays.fill(realHeights, (short)1);
		DEMTile dt = new DEMTile(0, 0, 64,64, realHeights);
		int bsLen = dt.getBitStreamLen();
		assertEquals(0, bsLen);
		assertEquals(1, dt.getBaseHeight());
		assertEquals(0, dt.getMaxDeltaHeight());
		assertEquals(0, dt.getEncodingType());
	}	

	@Test
	public void testAllZeroOneUndef() throws Exception {
		short[] realHeights = new short[64*64];
		realHeights[63 * 64] = HGTReader.UNDEF;
		byte[] exprectedRes = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xC0, 0x3e};
		DEMTile dt = new DEMTile(0, 0, 64,64, realHeights);
		int bsLen = dt.getBitStreamLen();
		assertEquals(12, bsLen);
		assertEquals(0, dt.getBaseHeight());
		assertEquals(1, dt.getMaxDeltaHeight());
		assertEquals(2, dt.getEncodingType());
		String res = Arrays.toString(dt.getBitStream());
		assertEquals(Arrays.toString(exprectedRes), res); 
	}	
	
} 