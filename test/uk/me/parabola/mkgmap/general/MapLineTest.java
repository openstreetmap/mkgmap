/*
 * Copyright (C) 2012.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Coord;

public class MapLineTest {

	@Test
	public void TestInsertPointsAtStart(){
		List<Coord> points1 = new ArrayList<Coord>(){{
			add(new Coord(30,55));
			add(new Coord(30,65));
			add(new Coord(20,65));
			add(new Coord(20,55));
		}};
		List<Coord> points2 = new ArrayList<Coord>(){{
			add(new Coord(10,20));
			add(new Coord(30,30));
			add(new Coord(30,55));
		}};

		MapLine ml = new MapLine();
		ml.setPoints(new ArrayList<>(points1));
		assertEquals(points1.size(), ml.getPoints().size());
		ml.insertPointsAtStart(points2);
		assertEquals(6, ml.getPoints().size());
		assertTrue(ml.getPoints().get(0).equals(new Coord(10,20)));
		assertTrue(ml.getPoints().get(2).equals(new Coord(30,55)));
		assertTrue(ml.getPoints().get(5).equals(new Coord(20,55)));
	}
}
