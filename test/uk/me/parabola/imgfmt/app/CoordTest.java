/*
 * Copyright (C) 2014.
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
package uk.me.parabola.imgfmt.app;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test some basic methods of the Coord class regarding distance and bearing calculations
 * @author GerdP
 *
 */
public class CoordTest {
	Coord pLAX = new Coord(33.95, -118.4);
	Coord pJFK = new Coord(40.6333333333, -73.7833333333333333);
	Coord pD = new Coord(34.5, -116.5);
	Coord p0_10 = new Coord (0.0, 10.0);
	Coord p1_10 = new Coord (1.0, 10.0);
	Coord p1_11 = new Coord (1.0, 11.0);
	Coord p60_10 = new Coord (60.0, 10.0);
	Coord p61_11 = new Coord (61.0, 11.0);
	
	/**
	 */
	@Test
	public void testBearingGC() {
		assertEquals(65.892222, pLAX.bearingToOnGreatCircle(pJFK, true), 0.1);
		assertEquals(0.0, p0_10.bearingToOnGreatCircle(p1_10, true), 0.001);
		assertEquals(89.991388, p1_10.bearingToOnGreatCircle(p1_11, true), 0.001);
		assertEquals(44.99555, p0_10.bearingToOnGreatCircle(p1_11, true), 0.001);
	}
	@Test
	public void testBearingRhumb() {
		assertEquals(79.32388, pLAX.bearingToOnRhumbLine(pJFK, true), 0.1);
		assertEquals(0, p0_10.bearingToOnRhumbLine(p1_10, true), 0.001);
		assertEquals(90, p1_10.bearingToOnRhumbLine(p1_11, true), 0.001);
		assertEquals(44.99861, p0_10.bearingToOnRhumbLine(p1_11, true), 0.001);
		assertEquals(26.214722, p60_10.bearingToOnRhumbLine(p61_11, true), 0.001);
	}
	
	@Test
	public void testDistanceRhumb() {
		// http://www.movable-type.co.uk/scripts/latlong.html says 4011 km  for R=6371 km
		assertEquals(4011000 * Coord.R / 6371000, pLAX.distanceOnRhumbLine(pJFK), 1000);
		assertEquals(4011000 * Coord.R / 6371000, pJFK.distanceOnRhumbLine(pLAX), 1000);
		assertEquals(Coord.U/360, p1_10.distanceOnRhumbLine(p1_11), 20);
		assertEquals(Coord.U/360, p0_10.distanceOnRhumbLine(p1_10), 20);
		assertEquals(157200*Coord.R / 6371000, p0_10.distanceOnRhumbLine(p1_11), 200);
		assertEquals(123900*Coord.R / 6371000, p60_10.distanceOnRhumbLine(p61_11), 200);
	}
	
	@Test
	public void testDistanceGC() {
		// http://www.movable-type.co.uk/scripts/latlong.html says 3973 km  for R=6371 km
		assertEquals(3973000 * Coord.R / 6371000, pLAX.distanceHaversine(pJFK), 1000);
		assertEquals(3973000 * Coord.R / 6371000, pJFK.distanceHaversine(pLAX), 1000);
		assertEquals(111300, p1_10.distanceHaversine(p1_11), 100);
		assertEquals(111300, p0_10.distanceHaversine(p1_10), 100); 
		assertEquals(157400, p0_10.distanceHaversine(p1_11), 100);
		assertEquals(124100, p60_10.distanceHaversine(p61_11), 100);
	}

}
