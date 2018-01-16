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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Area;

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
		Area bbox = new Area(-0.4296875, -90.9, 0.0, -90.0);
		HGTConverter hgtConverter = new HGTConverter(HGT_PATH, bbox, null);
		// test data contains sone islands with volcanos, bbox corners are all in the ocean
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMaxLat() * 256, bbox.getMaxLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMinLong() * 256));
		assertEquals(0, hgtConverter.getElevation(bbox.getMinLat() * 256, bbox.getMaxLong() * 256));
	}

} 
