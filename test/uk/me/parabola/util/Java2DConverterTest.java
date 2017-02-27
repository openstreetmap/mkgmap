/*
 * Copyright (C) 2006, 2012.
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
package uk.me.parabola.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.filters.ShapeMergeFilter;

public class Java2DConverterTest {

	/**
	 * Test if a polygon converted and converted back is equal
	 */
	@Test
	public void testPolygonConversion() throws Exception {
		List<Coord> polygon = new ArrayList<Coord>();
		polygon.add(new Coord(0,0));
		polygon.add(new Coord(100,10));
		polygon.add(new Coord(120,89));
		polygon.add(new Coord(20,44));
		polygon.add(new Coord(50,43));
		polygon.add(new Coord(0,0)); // Note that shape is not closed with identical points
		
		Area a = Java2DConverter.createArea(polygon);
		List<List<Coord>> convPolygon = Java2DConverter.areaToShapes(a);
		List<Coord> singularPolygon = Java2DConverter.singularAreaToPoints(a);
		
		assertEquals(convPolygon.size(), 1);
		assertEquals(polygon, convPolygon.get(0));
		assertEquals(polygon, singularPolygon);
    assertTrue(convPolygon.get(0).get(0) ==  convPolygon.get(0).get(convPolygon.get(0).size()-1));
    assertTrue(singularPolygon.get(0) ==  singularPolygon.get(singularPolygon.size()-1));
	}
	
	@Test
	public void testPolygonConversionWithEqualPoints() throws Exception {
		Path2D path1 = new Path2D.Double();
		Path2D path2 = new Path2D.Double();
		Path2D path3 = new Path2D.Double();
		path1.moveTo(0,0);
		path1.lineTo(100,-10);
		path1.lineTo(50,-43);
		path1.closePath();

		path2.moveTo(0,0);
		path2.lineTo(100,-10);
		path2.lineTo(50,-43);
		path2.lineTo(-0.0001,0); // point that is equal to closing points (in map units)  
		path2.closePath();
		
		path3.moveTo(0,0);
		path3.lineTo(100,-10);
		path3.lineTo(50,-43);
		path3.lineTo(-0.5001,0); // point that is not equal to closing points (in map units)  
		path3.closePath();
		
		Area a1 = new Area(path1);
		Area a2 = new Area(path2);
		Area a3 = new Area(path3);
		
		List<List<Coord>> convPolygon1 = Java2DConverter.areaToShapes(a1);
		List<List<Coord>> convPolygon2 = Java2DConverter.areaToShapes(a2);
		List<List<Coord>> convPolygon3 = Java2DConverter.areaToShapes(a3);
		List<Coord> singularPolygon1 = Java2DConverter.singularAreaToPoints(a1);
		List<Coord> singularPolygon2 = Java2DConverter.singularAreaToPoints(a2);
		List<Coord> singularPolygon3 = Java2DConverter.singularAreaToPoints(a3);
		
		assertTrue(a1.equals(a2) == false);
		assertTrue(a1.equals(a3) == false);
		assertTrue(a2.equals(a3) == false);
		assertEquals(1, convPolygon1.size());
		assertEquals(1, convPolygon2.size());
		assertEquals(1, convPolygon3.size());
		assertTrue(Arrays.deepEquals(convPolygon1.toArray(), convPolygon2.toArray()) == true); 
		assertTrue(Arrays.deepEquals(convPolygon1.toArray(), convPolygon3.toArray()) == false); 
		assertEquals(4, convPolygon1.get(0).size());
		assertEquals(4, convPolygon2.get(0).size());
		assertEquals(5, convPolygon3.get(0).size());
		assertEquals(4,singularPolygon1.size());
		assertEquals(4,singularPolygon2.size());
		assertEquals(5,singularPolygon3.size());

		assertTrue(convPolygon1.get(0).get(0) ==  convPolygon1.get(0).get(convPolygon1.get(0).size()-1));
		assertTrue(convPolygon2.get(0).get(0) ==  convPolygon2.get(0).get(convPolygon2.get(0).size()-1));
		assertTrue(convPolygon3.get(0).get(0) ==  convPolygon3.get(0).get(convPolygon3.get(0).size()-1));
		assertTrue(singularPolygon1.get(0) ==  singularPolygon1.get(singularPolygon1.size()-1));
		assertTrue(singularPolygon2.get(0) ==  singularPolygon2.get(singularPolygon2.size()-1));
		assertTrue(singularPolygon3.get(0) ==  singularPolygon3.get(singularPolygon3.size()-1));
	}
	
	@Test
	public void testPolygonConversionAt180() throws Exception {
		// various calculations near 180.0 
		List<Coord> points1 = new ArrayList<>();
		points1.add(new Coord(1.0,180.0));
		points1.add(new Coord(0.0,180.0));
		points1.add(new Coord(1.0,179.0));
		points1.add(points1.get(0));
		Area a1 = Java2DConverter.createArea(points1);
		uk.me.parabola.imgfmt.app.Area bbox = Java2DConverter.createBbox(a1);
		for (Coord co : points1)
			assertTrue(bbox.contains(co));
		Area awtPlanet = Java2DConverter.createBoundsArea(uk.me.parabola.imgfmt.app.Area.PLANET);
		a1.intersect(awtPlanet);
		assertTrue(a1.isSingular());
		List<Coord> points2 = Java2DConverter.singularAreaToPoints(a1);
		long testVal1 = ShapeMergeFilter.calcAreaSizeTestVal(points1);
		long testVal2 = ShapeMergeFilter.calcAreaSizeTestVal(points2);
		assertEquals(testVal1, testVal2);
	}

	@Test
	public void testPolygonConversionAtMinus180() throws Exception {
		// various calculations near 180.0 
		List<Coord> points1 = new ArrayList<>();
		points1.add(new Coord(-1.0,-180.0));
		points1.add(new Coord(0.0,-180.0));
		points1.add(new Coord(-1.0,-179.0));
		points1.add(points1.get(0));
		Area a1 = Java2DConverter.createArea(points1);
		uk.me.parabola.imgfmt.app.Area bbox = Java2DConverter.createBbox(a1);
		for (Coord co : points1)
			assertTrue(bbox.contains(co));
		Area awtPlanet = Java2DConverter.createBoundsArea(uk.me.parabola.imgfmt.app.Area.PLANET);
		a1.intersect(awtPlanet);
		assertTrue(a1.isSingular());
		List<Coord> points2 = Java2DConverter.singularAreaToPoints(a1);
		long testVal1 = ShapeMergeFilter.calcAreaSizeTestVal(points1);
		long testVal2 = ShapeMergeFilter.calcAreaSizeTestVal(points2);
		assertEquals(testVal1, testVal2);
	}
}
