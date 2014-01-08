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

package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapShape;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ShapeMergeFilterTest {
	private static final HashMap<Integer,Coord> map = new HashMap<Integer,Coord>(){
		{
			for (int lat = 0; lat < 100; lat +=5){
				for (int lon = 0; lon < 100; lon += 5){
					Coord co = new Coord(lat,lon);
					put(lat*1000 + lon,co);
				}
			}
		}
	};
	/**
	 */
	@Test
	public void testNonOverlapping(){
		MapShape s1 = new MapShape(1);
		MapShape s2 = new MapShape(2);
		s1.setMinResolution(22);
		s2.setMinResolution(22);
		List<Coord> points1 = new ArrayList<>();
		points1.add(getPoint(15,10));
		points1.add(getPoint(30,25));
		points1.add(getPoint(25,30));
		points1.add(getPoint(15,35));
		points1.add(getPoint(5,20));
		points1.add(points1.get(0)); // close
		
		List<Coord> points2 = new ArrayList<>();
		points2.add(getPoint(25,30));
		points2.add(getPoint(30,35));
		points2.add(getPoint(20,40));
		points2.add(getPoint(15,35));
		points2.add(points2.get(0)); // close
		
		
		s1.setPoints(points1);
		s2.setPoints(points2);
		
		for (int i = 1; i < points2.size(); i++){
			points2.remove(0);
			Collections.rotate(points2, i);
			points2.add(points2.get(0));
			s2.setPoints(points2);
			ShapeMergeFilter smf = new ShapeMergeFilter(24);
			List<MapShape> res = smf.merge(Arrays.asList(s1,s2), 0);
			assertTrue(res != null);
			assertEquals(1, res.size() );
			assertEquals(8, res.get(0).getPoints().size());
		}
	}
	
	Coord getPoint(int lat, int lon){
		Coord co = map.get(lat*1000+lon);
		assert co != null;
		return co;
	}
}
