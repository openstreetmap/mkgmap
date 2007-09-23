/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 02-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.test;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;


/**
 * A test map that contains a grid of each point, line and polygon type that
 * is possible.  The name of each element is set to be the numeric type (and
 * sub-type for points).
 *
 * This can be used to decode the type_id's.
 *
 * Instructions for use:  If you run this program with the environment
 * variables BASE_LAT and BASE_LONG set to something just SW of where you
 * are then the map generated will be located near where you are.  Otherwise
 * the default location is at (51.7, 0.24).
 *
 * You can then use the find facility of your GPS to
 * show the near-by points.  When viewing a category the menu key will allow
 * you to select finer categories.
 *
 * @author Steve Ratcliffe
 */
class AllElements {

	// These values are perhaps bias a bit towards places at mid latitues,
	// adjust as required.
	private static final double ELEMENT_SPACING = 0.002;
	private static final double ELEMENT_SIZE = 0.001;

	// I don't know what the max types and subtypes actually are, adjust if
	// there seems to be more beyond.
	private static final int MAX_POINT_TYPE = 0x7f;
	private static final int MAX_POINT_SUB_TYPE = 0x30;

	// we draw lines and polygons in a 16x16 square (or whatever is here).
	private static final int MAX_LINE_TYPE_X = 8;
	private static final int MAX_LINE_TYPE_Y = 8;
	private static final int MAX_SHAPE_TYPE_X = 12;
	private static final int MAX_SHAPE_TYPE_Y = 12;

	/**
	 * Loading the map in this case means generating it.
	 *
	 * @param mapper Used to collect the generated points etc.
	 */
	public void load(MapCollector mapper) {
		double baseLat = 51.7;
		double baseLong = 0.24;

		String sBaseLat = System.getenv("BASE_LAT");
		String sBaseLong = System.getenv("BASE_LONG");

		if (sBaseLat != null)
			baseLat = Double.valueOf(sBaseLat);
		if (sBaseLong != null)
			baseLong = Double.valueOf(sBaseLong);

		drawTestMap(mapper, baseLat, baseLong);
	}

	/**
	 * We draw points, lines and polygons separately.  They should be in
	 * order from west to east of the generated map, starting in the bottom
	 * left hand corner (SW).
	 *
	 * @param map Collector for the generated points etc.
	 * @param startLat The S coord.
	 * @param startLong The W coord.
	 */
	private void drawTestMap(MapCollector map, double startLat, double startLong) {
		double lat = startLat;
		double lng = startLong;

		drawPoints(map, lat, lng);

		lng += MAX_POINT_TYPE * ELEMENT_SPACING;
		drawLines(map, lat, lng);

		lng += MAX_LINE_TYPE_X * ELEMENT_SPACING;
		drawPolygons(map, lat, lng);
	}

	private void drawPoints(MapCollector mapper, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		for (int type = 0; type < MAX_POINT_TYPE; type++) {
			for (int subtype = 0; subtype < MAX_POINT_SUB_TYPE; subtype++) {

				MapPoint point = new MapPoint();

				double baseLat = lat + subtype * ELEMENT_SPACING;
				double baseLong = lon + type * ELEMENT_SPACING;

				point.setMinResolution(10);
				point.setName("0x" + Integer.toHexString(type)
								+ ','
								+ "0x" + Integer.toHexString(subtype));

				point.setLocation(new Coord(baseLat, baseLong));
				point.setType(type);
				point.setSubType(subtype);

				mapper.addPoint(point);
				mapper.addToBounds(point.getLocation()); // XXX shouldn't be needed.
			}
		}
	}

	private void drawLines(MapCollector mapper, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		for (int x = 0; x < MAX_LINE_TYPE_X; x++) {
			for (int y = 0; y < MAX_LINE_TYPE_Y; y++) {
				int type = x*MAX_LINE_TYPE_X + y;
				if ((type & 0xc0) != 0)
					break;

				MapLine line = new MapLine();
				line.setMinResolution(10);
				line.setName("0x" + Integer.toHexString(type));

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;

				List<Coord> coords = new ArrayList<Coord>();

				Coord co = new Coord(baseLat, baseLong);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				line.setType(type);
				line.setPoints(coords);

				mapper.addLine(line);
			}
		}
	}


	private void drawPolygons(MapCollector mapper, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		for (int x = 0; x < MAX_SHAPE_TYPE_X; x++) {
			for (int y = 0; y < MAX_SHAPE_TYPE_Y; y++) {
				int type = x*16 + y;
				if ((type & 0x80) != 0)
					break;

				//Polygon pg = div.createPolygon("0x" + Integer.toHexString(type));

				MapShape shape = new MapShape();
				shape.setMinResolution(10);
				shape.setName("0x" + Integer.toHexString(type));

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;

				List<Coord> coords = new ArrayList<Coord>();

				Coord co = new Coord(baseLat, baseLong);
				//pg.addCoord(co);
				coords.add(co);

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat, baseLong);
				coords.add(co);
				mapper.addToBounds(co);

				shape.setType(type);
				shape.setPoints(coords);

				mapper.addShape(shape);
			}
		}
	}
}
