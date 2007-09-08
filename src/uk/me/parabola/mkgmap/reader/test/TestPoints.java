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
 * Create date: 03-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.test;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapLine;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

/**
 * This is a map that contains many points in a dense grid.  It is going to
 * be used to investigate the 'find' bug.
 *
 * @author Steve Ratcliffe
 */
public class TestPoints {
	private static final double ELEMENT_SPACING = 0.02;

	private Properties props;
	//private static final int MAX_LINE_TYPE_X = 100;
	//private static final int MAX_LINE_TYPE_Y = 50;
	private static final double ELEMENT_SIZE = 0.002;

	/**
	 * Loading the map in this case means generating it.
	 *
	 * @param mapper Used to collect the generated points etc.
	 * @param props User supplied properties.
	 */
	public void load(MapCollector mapper, Properties props) {
		this.props = props;

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
	 * @param mapper Collector for the generated points etc.
	 * @param startLat The S coord.
	 * @param startLong The W coord.
	 */
	private void drawTestMap(MapCollector mapper, double startLat, double startLong) {
		double lat = startLat;
		double lng = startLong;

		String s = props.getProperty("npoints");
		int npoints = 10;
		if (s != null)
			npoints = Integer.valueOf(s);

		for (int x = 0; x < npoints; x++) {
			for (int y = 0; y < npoints; y++) {

				MapPoint point = new MapPoint();

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lng + x * ELEMENT_SPACING;

				point.setMinResolution(24 - (x & 0x7));
				point.setName("P " + (x*npoints + y));

				point.setLocation(new Coord(baseLat, baseLong));
				point.setType(0x2c);
				point.setSubType(y & 0xf);

				mapper.addPoint(point);
				mapper.addToBounds(point.getLocation()); // XXX shouldn't be needed.
			}
		}

		// I think the 'find' bug only shows when there are lines, so lets add
		// some.
		for (int x = 0; x < npoints; x++) {
			for (int y = 0; y < npoints; y++) {
				int type = x*16 + y;
				type &= 0xf;

				MapLine line = new MapLine();
				line.setMinResolution(10);
				line.setName("0x" + Integer.toHexString(type));

				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lng + x * ELEMENT_SPACING;

				List<Coord> coords = new ArrayList<Coord>();

				Coord co;
				for (int i = 0; i < 5; i++) {
					co = new Coord(baseLat + i*ELEMENT_SIZE, baseLong + i*ELEMENT_SIZE);
					coords.add(co);
					mapper.addToBounds(co);
				}

				line.setType(type);
				line.setPoints(coords);

				mapper.addLine(line);
			}
		}

	}

}
