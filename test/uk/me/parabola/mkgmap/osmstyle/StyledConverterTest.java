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
 * Create date: 02-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

import org.junit.Test;


/**
 * High level tests of the complete converter chain, using an actual
 * rules file.
 */
public class StyledConverterTest {
	private static final String LOC = "classpath:teststyles";
	private OsmConverter converter;
	private final List<MapLine> lines = new ArrayList<MapLine>();

	@Test
	public void testConvertWay() throws FileNotFoundException {
		converter = makeConverter("simple");
		Way way = makeWay();
		way.addTag("highway", "primary");
		way.addTag("x", "y");
		converter.convertWay(way);
		converter.end();
		
		assertEquals("line converted", 1, lines.size());
		assertEquals("line from highway", 0x2, lines.get(0).getType());
	}

	@Test
	public void testNullPointerFromSecondMatch() throws FileNotFoundException {
		converter = makeConverter("simple");
		Way way = makeWay();
		way.addTag("highway", "primary");
		way.addTag("x", "z");
		converter.convertWay(way);
		converter.end();
		assertEquals("line converted", 1, lines.size());
		assertEquals("line from x=y", 0x3, lines.get(0).getType());
	}

	@Test
	public void testModifyingTagsInUse() throws FileNotFoundException {
		Way way = makeWay();
		way.addTag("name", "bar");
		way.addTag("highway", "other");
		way.addTag("a", "z");
		way.addTag("z", "z");
		converter = makeConverter("simple");
		converter.convertWay(way);
		converter.end();

		assertEquals("line converted", 1, lines.size());
		assertEquals("line", 0x12, lines.get(0).getType());
	}

	/**
	 * Test the overlay feature, when one line is duplicated with different
	 * types.
	 */
	@Test
	public void testOverlay() throws FileNotFoundException {
		Way way = makeWay();
		way.addTag("highway", "overlay");
		converter = makeConverter("simple");
		converter.convertWay(way);
		converter.end();

		assertEquals("lines produced", 3, lines.size());
		assertEquals("first line is 1", 1, lines.get(0).getType());
		assertEquals("second line is 2", 2, lines.get(1).getType());
		assertEquals("third line is 3", 3, lines.get(2).getType());
	}

	/**
	 * Test styles that are derived from others.  Rules should behave as
	 * if they were combined in order with the base rule last.
	 *
	 * This test contains the exact same rule that occurs in the base
	 * style with a different type.  It is the derived style type that we
	 * should see.
	 */
	@Test
	public void testBaseStyle() throws FileNotFoundException {
		converter = makeConverter("derived");
		Way way = makeWay();
		way.addTag("overridden", "xyz");
		converter.convertWay(way);
		

		// Now try a rule that is only in the base 'simple' file.
		way = makeWay();
		way.addTag("highway", "primary");
		converter.convertWay(way); 
		converter.end();
		assertEquals("lines converted", 2, lines.size());
		assertEquals("derived type", 0x22, lines.get(0).getType());
		assertEquals("from base style", 0x3, lines.get(1).getType());
	}

	/**
	 * The derived style has a rule that is not in the base style.  Call with
	 * a way that would match a rule in the base style and with the different
	 * rule in the derived style.  You should get the type from the derived
	 * style.
	 * @throws FileNotFoundException
	 */
	@Test
	public void testOverridePriority() throws FileNotFoundException {
		converter = makeConverter("derived");
		Way way = makeWay();
		way.addTag("highway", "other"); // this would match in the base
		way.addTag("derived", "first"); // this matches in the derived style
		converter.convertWay(way);
		converter.end();
		
		assertEquals("lines converted", 1, lines.size());
		assertEquals("derived type", 0x25, lines.get(0).getType());
	}

	@Test
	public void testMultipleBase() throws FileNotFoundException {

		convertTag("a", "a");
		assertEquals(1, lines.get(0).getType());

		convertTag("b", "b");
		assertEquals(1, lines.get(0).getType());

		convertTag("c", "c");
		assertEquals(1, lines.get(0).getType());

		convertTag("d", "d");
		assertEquals(1, lines.get(0).getType());

		convertTag("override", "ab");
		assertEquals(2, lines.get(0).getType());

		for (String s : new String[]{"ac", "bc"}) {
			convertTag("override", s);
			assertEquals(3, lines.get(0).getType());
		}

		for (String s : new String[]{"ad", "bd", "cd"}) {
			convertTag("override", s);
			assertEquals(4, lines.get(0).getType());
		}
	}

	private Way convertTag(String key, String value) throws FileNotFoundException {
		lines.clear();
		Way way = makeWay();
		way.addTag(key, value);
		converter = makeConverter("d");
		converter.convertWay(way);
		converter.end();
		return way;
	}

	@Test
	public void testFileConflicts() throws FileNotFoundException {
		converter = makeConverter("waycombine");
		Way w = makeWay();
		w.addTag("highway", "pedestrian");
		converter.convertWay(w);
		converter.end();

		assertEquals("lines converted", 1, lines.size());

		// In particular both 1 and 7 are wrong here.
		assertEquals("found pedestrian type", 6, lines.get(0).getType());
	}

	@Test
	public void testFinalizer() throws FileNotFoundException {
		// test three ways
		// the first two ways should get the name "OK" by the finalize part
		// the third way should not be handled by the finalize part
		String[][] tests = new String[][] {{"residential","OK"}, {"track","OK"},{"secondary",null}};
		
		for (String[] test : tests ) {
			// clear the lines list for each test
			lines.clear();
			converter = makeConverter("finalize");
			Way w = makeWay();
			w.addTag("highway", test[0]);
			converter.convertWay(w);
			converter.end();

			assertEquals("lines converted", 1, lines.size());
			assertEquals("wrong name set by the finalize block", test[1], lines.get(0).getName());
		}
		
		// check if continue works
		lines.clear();
		converter = makeConverter("finalize");
		Way w = makeWay();
		w.addTag("highway", "trunk");
		converter.convertWay(w);
		converter.end();

		assertEquals("lines converted", 2, lines.size());
		assertEquals("wrong name set by the finalize block", "OK", lines.get(0).getName());
		assertEquals("wrong name set by the finalize block", null, lines.get(1).getName());
		
	}
	
	private Way makeWay() {
		Way way = new Way(1);
		way.addPoint(new Coord(100, 100));
		way.addPoint(new Coord(100, 102));
		way.addPoint(new Coord(100, 103));
		return way;
	}

	private StyledConverter makeConverter(String name) throws FileNotFoundException {
		Style style = new StyleImpl(LOC, name);
		MapCollector coll = new MapCollector() {
			public void addToBounds(Coord p) { }

			// could save points in the same way as lines to test them
			public void addPoint(MapPoint point) { }

			public void addLine(MapLine line) {
				// Save line so that it can be examined in the tests.
				assertNotNull("points are not null", line.getPoints());
				lines.add(line);
			}

			public void addShape(MapShape shape) { }

			public void addRoad(MapRoad road) {
				lines.add(road);
			}

			public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode, byte exceptMask) { }

			public void addThroughRoute(long junctionNodeId, long roadIdA, long roadIdB) { }
		};

		return new StyledConverter(style, coll, new EnhancedProperties());
	}
}
