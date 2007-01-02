/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 02-Jan-2007
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.app.*;
import uk.me.parabola.imgfmt.Utils;

/**
 * A test map that contains a grid points.  Each point has a different
 * type and subtype in sequence.  the name is also the type_id,sub_id.
 *
 * This can be used to decode the type_id's.
 *
 * Instructions for use:  If you run this program with a latitude and longitude
 * that are near your area, you can then use the find facility of your GPS to
 * show the near-by points.  When viewing a category the menu key will allow
 * you to select finer categories.
 *
 * @author Steve Ratcliffe
 */
public class MakeTestPointMap extends AbstractTestMap {

	public static void main(String[] args)  {
		MakeTestPointMap tm = new MakeTestPointMap();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double lat, double lng) {
		drawPoints(map, div, lat, lng);
	}

	protected void writeOverviews(Map map) {
		// nothing to do here
	}

	private void drawPoints(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		double space = 0.002;

		div.setHasPoints(true);
		map.startPoints();

		for (int x = 0; x < 0x80; x++) {
			for (int y = 0; y < 0x20; y++) {
				int type = x;
				int subtype = y;

				Point point = map.createPoint(div,
						"0x" + Integer.toHexString(type)
								+ ","
								+ "0x" + Integer.toHexString(subtype));
				
				double base_lat = lat + y * space;
				double base_lon = lon + x * space;

				point.setLatitude(Utils.toMapUnit(base_lat));
				point.setLongitude(Utils.toMapUnit(base_lon));
				point.setType(type);
				point.setSubtype(subtype);
				map.addMapObject(point);
				map.addPointOverview(new PointOverview(type, subtype));
			}
		}

		map.addPointOverview(new PointOverview(1, 1));
	}

}
