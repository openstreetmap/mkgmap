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
import java.util.ArrayList;
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
		polygon.add(new Coord(0,0));
		
		Area a = Java2DConverter.createArea(polygon);
		List<List<Coord>> convPolygon = Java2DConverter.areaToShapes(a);
		List<Coord> singularPolygon = Java2DConverter.singularAreaToPoints(a);
		
		assertEquals(convPolygon.size(), 1);
		assertEquals(polygon, convPolygon.get(0));
		assertEquals(polygon, singularPolygon);
	}
	
	
}
