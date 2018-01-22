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
package uk.me.parabola.mkgmap.reader.hgt;

import static org.junit.Assert.*;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.dem.DEMFile;

/**
 * Tests for HGTConverter.
 * @author Gerd Petermann
 *
 */
public class HGTConverterTest {
	private final static String HGT_PATH = "test/resources/in/hgt";
	@Test
	public void testLat0Top() throws Exception {
		
		// top is exactly at 0, caused ArrayIndexOutOfBoundsException with r4065
		Area bbox = new Area(-1.04296875, -90.9, 0.0, -90.0);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null, DEMFile.EXTRA);
		// test data contains sone islands with volcanos, bbox corners are all in the ocean
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMaxLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMaxLong() * 256));
		
		// retrieve value at offset 646834 (0x9DEB2) in S01W091.hgt
		double hgtDis = 1.0D/1200;
		int lat32 =  (int) ((Math.round(-0.22395 / hgtDis) * hgtDis) * (1<<29) / 45);
		int lon32 =  (int) ((Math.round(-90.71 / hgtDis) * hgtDis) * (1<<29) / 45);
		assertEquals(308, hgtConverter.getElevation(lat32, lon32));
	}

	@Test
	public void testLat0Bottom() throws Exception {
		Area bbox = new Area(0, -90.9, 0.1, -90.0);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null, DEMFile.EXTRA);
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMaxLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMaxLong() * 256));
	}

	@Test
	public void testLon0Left() throws Exception {
		
		Area bbox = new Area(0, 0.1, 0.1, 1.0);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null, DEMFile.EXTRA);
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMaxLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMaxLong() * 256));
	}

	@Test
	public void testLon0Right() throws Exception {
		Area bbox = new Area(0, 0.1, 0.1, 0.0);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null, DEMFile.EXTRA);
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMaxLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMaxLong() * 256));
	}

	@Test
	public void testReadHeight() throws Exception {
		
		Area bbox = new Area(-1.04296875, -91.1, 0.1, -89.9);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null, DEMFile.EXTRA);

		int hgtRes = 1200;
		double hgtDis = 1.0D/hgtRes;
		int hgtX = 348;
		int hgtY = 931;
		
		int fileOffset = 2 * ((hgtRes - hgtY) * (hgtRes + 1) + hgtX);
		assertEquals(646834, fileOffset);
		// get a value from S01W091.hgt
		double hgtLat = 0.0 - (hgtRes - hgtY) * hgtDis;
		double hgtLon = -91.0 + hgtX * hgtDis;
		// convert to DEM units
		int lat32 =  (int) (hgtLat / HGTConverter.FACTOR);
		int lon32 =  (int) (hgtLon / HGTConverter.FACTOR);
		assertTrue(bbox.contains(new Coord(hgtLat, hgtLon)));
		assertEquals(308, hgtConverter.getElevation(lat32, lon32));
		
	}
	
} 
