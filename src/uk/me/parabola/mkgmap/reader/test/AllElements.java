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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.osm.GType;


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
 * To run, something like:
 * java -jar mkgmap.jar --gmapsupp test-map:all-elements ...
 *
 * You can then use the find facility of your GPS to
 * show the near-by points.  When viewing a category the menu key will allow
 * you to select finer categories.
 *
 * @author Steve Ratcliffe
 */
class AllElements {

	// These values are perhaps bias a bit towards places at mid latitudes,
	// adjust as required.
	private static final double ELEMENT_SPACING = 0.002;
	private static final double ELEMENT_SIZE = 0.001;

	// I don't know what the max types and sub-types actually are, adjust if
	// there seems to be more beyond.
	private static final int MAX_POINT_TYPE = 0x7f;
	private static final int MAX_POINT_SUB_TYPE = 0x1f;

	// we draw lines and polygons in a 16x16 square (or whatever is here).
	private static final int MAX_LINE_TYPE_X = 4;
	private static final int MAX_LINE_TYPE_Y = 16;
	private static final int MAX_SHAPE_TYPE_X = 8;
	private static final int MAX_SHAPE_TYPE_Y = 16;
	private Properties configProps;

	public AllElements(Properties configProps) {
		this.configProps = configProps;
	}

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
		if (sBaseLat == null)
			sBaseLat = configProps.getProperty("base-lat");
		if (sBaseLong == null)
			sBaseLong = configProps.getProperty("base-long");

		if (sBaseLat != null)
			baseLat = Double.valueOf(sBaseLat);

		if (sBaseLong != null)
			baseLong = Double.valueOf(sBaseLong);
		
		drawTestMap(mapper, baseLat, baseLong, false);
// do same again but on different background without labels
		baseLat += (MAX_POINT_SUB_TYPE + 4) * ELEMENT_SPACING;  // assume taller than lines and areas
		drawBackground(mapper, baseLat, baseLong, MAX_POINT_SUB_TYPE + 3, MAX_POINT_TYPE + MAX_LINE_TYPE_X + MAX_SHAPE_TYPE_X + 4);
		drawTestMap(mapper, baseLat, baseLong, true);
	}

        private void drawBackground(MapCollector mapper, double startLat, double startLong, int nUp, int nAcross) {
		MapShape shape = new MapShape();
		int type = 0x51; // Wetlands // 0x4d; // glacier-white
		shape.setMinResolution(10);
		shape.setName("background");

		List<Coord> coords = new ArrayList<Coord>();

		Coord co = new Coord(startLat, startLong);
		coords.add(co);
		mapper.addToBounds(co);

		co = new Coord(startLat + (nUp * ELEMENT_SPACING), startLong);
		coords.add(co);
		mapper.addToBounds(co);

		co = new Coord(startLat + (nUp * ELEMENT_SPACING), startLong + (nAcross * ELEMENT_SPACING));
		coords.add(co);
		mapper.addToBounds(co);

		co = new Coord(startLat, startLong + (nAcross * ELEMENT_SPACING));
		coords.add(co);
		mapper.addToBounds(co);

		coords.add(coords.get(0));

		shape.setType(type);
		shape.setPoints(coords);

		mapper.addShape(shape);
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
	private void drawTestMap(MapCollector map, double startLat, double startLong, boolean hasBackground) {
		double lng = startLong;

		drawPoints(map, startLat, lng, hasBackground);

		lng += (MAX_POINT_TYPE + 1) * ELEMENT_SPACING;
		drawLines(map, startLat, lng, hasBackground);

		lng += (MAX_LINE_TYPE_X + 1) * ELEMENT_SPACING;
		drawPolygons(map, startLat, lng, hasBackground);
	}

	private void drawPoints(MapCollector mapper, double slat, double slon, boolean hasBackground) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		
		for (int maintype = 0; maintype <= MAX_POINT_TYPE; maintype++) {
//			for (int subtype = 0; subtype <= MAX_POINT_SUB_TYPE; subtype++) {
			for (int subtype = -1; subtype <= MAX_POINT_SUB_TYPE; subtype++) {
				// if maintype is zero, the subtype will be treated as the type
			    	// use subtype -1 to indicate no subtype and draw, say
			    	// point 0x23 under 0x2300 to check they are the same
				// The zero column is just for just to see 0x00
			    	int type;  // = (maintype << 8) + subtype;
				if (subtype < 0)
					type = maintype;
				else
					type = (maintype << 8) + subtype;

				MapPoint point = new MapPoint();

				double baseLat = lat + subtype * ELEMENT_SPACING;
				double baseLong = lon + maintype * ELEMENT_SPACING;

				point.setMinResolution(10);
				if (subtype < 0 ? hasBackground : !hasBackground)
					point.setName(GType.formatType(type));
				point.setLocation(new Coord(baseLat, baseLong));
				point.setType(type);

				mapper.addPoint(point);
				
				if (configProps.containsKey("verbose"))
					System.out.println("Generated POI " + GType.formatType(type) + " at " + point.getLocation().toDegreeString()); 
				mapper.addToBounds(point.getLocation()); // XXX shouldn't be needed.
				if (maintype == 0)
				    break;
			}
		}
	}

	private void drawLines(MapCollector mapper, double slat, double slon, boolean hasBackground) {
		
		double lat = slat + 0.004;
		double lon = slon + 0.002;
		int type = 0;
		for (int x = 0; x < MAX_LINE_TYPE_X; x++) {
			for (int y = 0; y < MAX_LINE_TYPE_Y; y++) {

				MapLine line = new MapLine();
				line.setMinResolution(10);
				if (!hasBackground)
					line.setName(GType.formatType(type));

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;
				List<Coord> coords = new ArrayList<Coord>();

				Coord co = new Coord(baseLat, baseLong);
				coords.add(co);
				mapper.addToBounds(co);
				if (configProps.containsKey("verbose"))
					System.out.println("Generated line " + GType.formatType(type) + " at " + co.toDegreeString());

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong +	 ELEMENT_SIZE + ELEMENT_SIZE/2);
				coords.add(co);
				mapper.addToBounds(co);

				line.setType(type);
				line.setPoints(coords);

				mapper.addLine(line);
				type++;
			}
		}
	}


	private void drawPolygons(MapCollector mapper, double slat, double slon, boolean hasBackground) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		int type = 0;
		for (int x = 0; x < MAX_SHAPE_TYPE_X; x++) {
			for (int y = 0; y < MAX_SHAPE_TYPE_Y; y++) {

				//Polygon pg = div.createPolygon("0x" + Integer.toHexString(type));

				MapShape shape = new MapShape();
				shape.setMinResolution(10);
				if (hasBackground)
					shape.setName(GType.formatType(type));

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;
				
				List<Coord> coords = new ArrayList<Coord>();

				Coord co = new Coord(baseLat, baseLong);
				//pg.addCoord(co);
				coords.add(co);
				mapper.addToBounds(co);
				if (configProps.containsKey("verbose"))
					System.out.println("Generated polygon " + GType.formatType(type) + " at " + co.toDegreeString());
				
				co = new Coord(baseLat + ELEMENT_SIZE, baseLong);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				co = new Coord(baseLat, baseLong + ELEMENT_SIZE);
				coords.add(co);
				mapper.addToBounds(co);

				coords.add(coords.get(0));

				shape.setType(type);
				shape.setPoints(coords);

				mapper.addShape(shape);
				type++;
			}
		}
	}
}
