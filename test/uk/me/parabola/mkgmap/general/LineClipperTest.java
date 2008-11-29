/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 29-Nov-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Steve Ratcliffe
 */
public class LineClipperTest {
	@Before
	public void setUp() {
		// Add your code here
	}

	@After
	public void tearDown() {
		// Add your code here
	}

	/**
	 * This is the example as given on the referenced web page.
	 * We now use integers instead of floats so the 101.425 from the
	 * example is just 101 here.
	 */
	@Test
	public void testExampleClip() {
		Area a = new Area(60, 70, 150, 230);
		Coord[] co = {
				new Coord(20, 30),
				new Coord(160, 280),
		};

		List<List<Coord>> listList = LineClipper.clip(a, Arrays.asList(co));
		assertTrue(!listList.isEmpty());

		Coord[] result = {
				new Coord(60, 101),
				new Coord(132, 230)
		};
		assertArrayEquals("example result", result, listList.get(0).toArray());
	}

	/**
	 * Test an original line that enters the area, leaves it and then goes back
	 * into the area.  This should give two lines in the result set.
	 */
	@Test
	public void testListClip() {
		// Add your code here
		Area a = new Area(100, 100, 200, 200);
		List<Coord> l = Arrays.asList(new Coord(20, 30),
				new Coord(40, 60),
				new Coord(102, 110),
				new Coord(150, 150),
				new Coord(210, 220),
				new Coord(190, 195)
				);
		List<List<Coord>> list = LineClipper.clip(a, l);

		// There should be exactly two lines
		assertEquals("should be two lines", 2, list.size());

		// No empty lists
		for (List<Coord> lco : list)
			assertTrue("empty list", !lco.isEmpty());

		// Check values
		Coord[] firstExpectedLine = {
				new Coord(100, 108),
				new Coord(102, 110),
				new Coord(150, 150),
				new Coord(192, 200)
		};
		assertArrayEquals(firstExpectedLine, list.get(0).toArray());
		Coord[] secondExpectedLine = {
				new Coord(194, 200),
				new Coord(190, 195)
		};
		assertArrayEquals(secondExpectedLine, list.get(1).toArray());
	}

	@Test
	public void testAllInside() {
		Area a = new Area(100, 100, 200, 200);
		List<Coord> l = Arrays.asList(
				new Coord(102, 110),
				new Coord(150, 150),
				new Coord(190, 195)
				);
		List<List<Coord>> list = LineClipper.clip(a, l);
		assertEquals("all lines inside", null, list);
	}
}
