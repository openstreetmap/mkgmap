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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import uk.me.parabola.mkgmap.filters.ShapeMergeFilter;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.GpxCreator;

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
	Coord russia1 = new Coord(3069580,8388608);
	Coord russia2 = new Coord(3105677,8388608);
	Coord special1 = new Coord(52.2452629, 21.0833536);
							
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

	@Test 
	public void testMakeBetweenAt180(){
		Coord russia3 = russia1.makeBetweenPoint(russia2, 0.5);
		assertEquals(russia3.getLongitude(), russia1.getLongitude());
	}
	
	@Test 
	public void destOnRhumLineAt180(){
		Coord russia3 = russia1.destOnRhumLine(1, 0.0);
		assertEquals(russia3.getLongitude(), russia1.getLongitude());
		Coord russia4 = russia1.destOnRhumLine(10000, 0.0);
		assertEquals(russia4.getLongitude(), russia1.getLongitude());
	}
	
	@Test
	public void testOffset() {
		assertEquals(pLAX, pLAX.offset(60, 100).offset(120, 100).offset(180, 100).offset(240, 100).offset(300, 100).offset(360, 100));
	}
	
	@Test
	public void testOverflow() {
		Coord c1 = new Coord(90.0,180.0);
		Coord c2 = new Coord(90.0,-180.0);
		assertFalse(c1.equals(c2));
		assertFalse(c1.highPrecEquals(c2));
		assertEquals(0,c1.distance(c2),0.0000001);
		assertEquals(0x800000, c1.getLongitude()); 
	}
	
	@SuppressWarnings("unused")
	@Test 
	public void testRounding() {
		if (Coord.DELTA_SHIFT + 24 == Integer.SIZE) {
			Coord c1 = new Coord(61.0000001, -23.0000001);
			Coord c2 = new Coord(61.0000000, -23.0000001);
			Coord c3 = new Coord(61.0000001, -23.0000000);
			assertFalse(c1.highPrecEquals(c2));
			assertFalse(c1.highPrecEquals(c3));
			assertFalse(c2.highPrecEquals(c3));
			assertTrue(c1.equals(c2));
			assertTrue(c1.equals(c3));
			assertTrue(c2.equals(c3));
			assertEquals(61.0000001d,c1.getLatDegrees(),0.0000001);
			assertEquals(61.0000000d,c2.getLatDegrees(),0.0000001);
			assertEquals(61.0000001d,c3.getLatDegrees(),0.0000001);
			assertEquals(-23.0000001d,c1.getLonDegrees(),0.0000001);
			assertEquals(-23.0000001d,c2.getLonDegrees(),0.0000001);
			assertEquals(-23.0000000d,c3.getLonDegrees(),0.0000001);
			double lat1 = -1;
			double lat2 = 1;
			double lon1 = -1;
			double lon2 = 1;
			for (int i = 0; i < 1000; i++) {
				Coord ct1 = new Coord(lat1,lon1);
				Coord ct2 = new Coord(lat2,lon2);
				assertEquals(lat1,ct1.getLatDegrees(),0.0000001);
				assertEquals(lat2,ct2.getLatDegrees(),0.0000001);
				assertEquals(lon1,ct1.getLonDegrees(),0.0000001);
				assertEquals(lon2,ct2.getLonDegrees(),0.0000001);
				lat1 = Math.nextAfter((float) lat1, 90);
				lat2 = Math.nextAfter((float) lat2, 90);
				lon1 = Math.nextAfter((float) lon1, 180);
				lon2 = Math.nextAfter((float) lon2, 180);
			}
		}
	}

	@Test
	public void alternativePos() {
		Coord c1 = new Coord(61.0000001, -23.0000001);
		List<Coord> alt1 = c1.getAlternativePositions();
		assertEquals(1, alt1.size());
		for (Coord c : alt1) {
			assertTrue(c.distance(c1) < 3);
			assertTrue(c.hasAlternativePos());
			List<Coord> altx = c.getAlternativePositions();
			assertFalse(altx.isEmpty());
		}
		Coord c2 = new Coord(61.1, -23.3);
		List<Coord> alt2 = c2.getAlternativePositions();
		assertEquals(3, alt2.size());
		for (Coord c : alt2) {
			assertTrue(c.distance(c2) < 3);
			assertTrue(c.hasAlternativePos());
			List<Coord> altx = c.getAlternativePositions();
			assertFalse(altx.isEmpty());
		}
		List<Coord> alt3 = special1.getAlternativePositions();
		GpxCreator.createGpx("e:/ld/special1", Collections.singletonList(special1), alt3);
	}

//	@Test
//	public void alternativePos2() {
//		double lon = 0.0;
//		double step = 0.00000000001;
//		int count = 0;
//		while (count < 1000) {
//			Coord c1 = new Coord (0,lon);
//			Coord c2 = new Coord (0, lon+step);
//			if (!c1.highPrecEquals(c2)) {
//				System.out.println(count + " " + lon);
//				count++;
//			}
//			lon += step;
//		}
//	}
	
	@Test
	public void testPlanet() throws Exception {
		final uk.me.parabola.imgfmt.app.Area planet = uk.me.parabola.imgfmt.app.Area.PLANET;
		Coord lowerLeft = new Coord(-90.0, -180.0);
		assertEquals(planet.getMinLat(), lowerLeft.getLatitude());
		assertEquals(-90.0, lowerLeft.getLatDegrees(), 0.0000001);
		assertEquals(planet.getMinLong(), lowerLeft.getLongitude());
		assertEquals(-180.0, lowerLeft.getLonDegrees(), 0.0000001);
		Coord upperRight = new Coord(90.0, 180.0);
		assertEquals(planet.getMaxLat(), upperRight.getLatitude());
		assertEquals(90.0, upperRight.getLatDegrees(), 0.0000001);
		assertEquals(planet.getMaxLong(), upperRight.getLongitude());
		assertEquals(180.0, upperRight.getLonDegrees(), 0.0000001);
		long testVal = ShapeMergeFilter.calcAreaSizeTestVal(planet.toCoords());
		double areaSizeCoords = (double) testVal / (2 * (1<<6) * (1<<6));
		areaSizeCoords = Math.abs(areaSizeCoords);
		double areaSizeBounds = (double) planet.getHeight() * planet.getWidth();
		assertEquals(areaSizeBounds, areaSizeCoords, 0.0001);
		boolean dir = Way.clockwise(planet.toCoords()); 
		assertFalse(dir);
	}
	
}
