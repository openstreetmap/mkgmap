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
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.filters.ShapeMergeFilter;
import uk.me.parabola.mkgmap.general.MapShape;

import org.junit.Test;
import static org.junit.Assert.*;

public class SutherlandHodgmanPolygonClipperTest {
	/**
	 * Test if a clipper works as expected
	 */
	@Test
	public void testClipping() throws Exception {
		List<Coord> points = new ArrayList<Coord>(){{
			add(new Coord(5,10));
			add(new Coord(25,10));
			add(new Coord(15,20));
			add(new Coord(15,30));
			add(new Coord(25,40));
			add(new Coord(5,40));
			add(new Coord(5,10)); // close with non-identical point
		}};
		Area clippingRect1 = new Area(0,5,15,45);
		Area clippingRect2 = new Area(15,5,35,45);
		MapShape ms = new MapShape();
		ms.setPoints(points);
		
		List<Coord> res1 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect1);
		
		//GpxCreator.createGpx("e:/ld/res1", res1);
		assertEquals(7, res1.size() );
		assertEquals(7, points.size() );
		List<Coord> res2 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect2);
		assertEquals(7, res2.size() );
		long testVal = ShapeMergeFilter.calcAreaSizeTestVal(points);
		long testVal2 = ShapeMergeFilter.calcAreaSizeTestVal(res1);
		long testVal3 = ShapeMergeFilter.calcAreaSizeTestVal(res2);
		assertEquals(testVal, testVal2+testVal3 );
	}

	@Test
	public void testClipping2() throws Exception {
		List<Coord> points = new ArrayList<Coord>(){{
			add(new Coord(5,10));
			add(new Coord(25,10));
			add(new Coord(15,20));
			add(new Coord(15,30));
			add(new Coord(25,40));
			add(new Coord(5,40));
			add(new Coord(5,10)); // close with non-identical point
		}};
		Area clippingRect1 = new Area(0,0,35,25);
		Area clippingRect2 = new Area(0,25,35,45);
		MapShape ms = new MapShape();
		ms.setPoints(points);
		
		List<Coord> res1 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect1);
		assertEquals(6, res1.size() );
		assertEquals(7, points.size() );
		List<Coord> res2 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect2);
		assertEquals(6, res2.size() );
		long testVal = ShapeMergeFilter.calcAreaSizeTestVal(points);
		long testVal2 = ShapeMergeFilter.calcAreaSizeTestVal(res1);
		long testVal3 = ShapeMergeFilter.calcAreaSizeTestVal(res2);
		assertEquals(testVal, testVal2+testVal3 );
	}

	@Test
	public void testDangling1() throws Exception {
		List<Coord> points = new ArrayList<Coord>(){{
			add(new Coord(5,10));
			add(new Coord(25,10));
			add(new Coord(15,20));
			add(new Coord(15,30));
			add(new Coord(25,40));
			add(new Coord(5,40));
			add(new Coord(5,10)); // close with non-identical point
		}};
		
		Area clippingRect1 = new Area(15,0,35,25);
		Area clippingRect2 = new Area(15,25,35,45);
		MapShape ms = new MapShape();
		ms.setPoints(points);
		List<Coord> res1 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect1);
		assertEquals(5, res1.size() );
		assertEquals(7, points.size() );
		List<Coord> res2 = SutherlandHodgmanPolygonClipper.clipPolygon(points, clippingRect2);
		assertEquals(5, res2.size() );
		long testVal2 = ShapeMergeFilter.calcAreaSizeTestVal(res1);
		long testVal3 = ShapeMergeFilter.calcAreaSizeTestVal(res2);
		assertEquals(testVal2, testVal3);
	}

}
