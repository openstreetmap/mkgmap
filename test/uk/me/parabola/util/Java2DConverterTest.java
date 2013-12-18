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

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;

import org.junit.Test;
import static org.junit.Assert.*;

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
		
	}
	
	
}
