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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Point;
import uk.me.parabola.imgfmt.app.PointOverview;
import uk.me.parabola.imgfmt.app.Polygon;
import uk.me.parabola.imgfmt.app.PolygonOverview;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.PolylineOverview;
import uk.me.parabola.imgfmt.app.Subdivision;

/**
 * A test map that contains a grid of each point, line and polygon type that
 * is possible.  The name of each element is set to be the numeric type (and
 * sub-type for points).
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
public class MakeTestElementMap extends AbstractTestMap {
	// These values are perhaps bias a bit towards places at mid latitues,
	// adjust as required.
	private static final double ELEMENT_SPACING = 0.002;
	private static final double ELEMENT_SIZE = 0.001;

	public static void main(String[] args)  {
		MakeTestPointMap tm = new MakeTestPointMap();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double startLat, double startLong) {
		double lat = startLat;
		double lng = startLong;

		drawPoints(map, div, lat, lng);

		lat += 256 * ELEMENT_SPACING;
		lng += 256 * ELEMENT_SPACING;
		drawLines(map, div, lat, lng);

		lat += 16 * ELEMENT_SPACING;
		lng += 16 * ELEMENT_SPACING;
		drawPolygons(map, div, lat, lng);
	}

	private void drawPoints(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		div.startPoints();

		for (int type = 0; type < 0xff; type++) {
			for (int subtype = 0; subtype < 0xff; subtype++) {

				Point point = div.createPoint("0x" + Integer.toHexString(type)
						+ ",0x" + Integer.toHexString(subtype));

				double baseLat = lat + subtype * ELEMENT_SPACING;
				double baseLong = lon + type * ELEMENT_SPACING;

				point.setLatitude(Utils.toMapUnit(baseLat));
				point.setLongitude(Utils.toMapUnit(baseLong));
				point.setType(type);
				point.setSubtype(subtype);
				map.addMapObject(point);
				map.addPointOverview(new PointOverview(type, subtype));
			}
		}

		map.addPointOverview(new PointOverview(1, 1));
	}

	private void drawLines(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		div.startLines();

		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				int type = x*16 + y;

				Polyline pl = div.createLine("0x" + Integer.toHexString(type));
				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;
				Coord co = new Coord(baseLat, baseLong);
				pl.addCoord(co);
				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				pl.addCoord(co);

				pl.setType(type);
				map.addMapObject(pl);
				map.addPolylineOverview(new PolylineOverview(type));
			}
		}

		map.addPolylineOverview(new PolylineOverview(0));
	}


	private void drawPolygons(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		div.startShapes();

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 16; y++) {
				int type = x*16 + y;

				Polygon pg = div.createPolygon("0x" + Integer.toHexString(type));
				double baseLat = lat + y * ELEMENT_SPACING;
				double baseLong = lon + x * ELEMENT_SPACING;
				Coord co = new Coord(baseLat, baseLong);
				pg.addCoord(co);
				co = new Coord(baseLat + ELEMENT_SIZE, baseLong);
				pg.addCoord(co);
				co = new Coord(baseLat + ELEMENT_SIZE, baseLong + ELEMENT_SIZE);
				pg.addCoord(co);
				co = new Coord(baseLat, baseLong + ELEMENT_SIZE);
				pg.addCoord(co);
				co = new Coord(baseLat, baseLong);
				pg.addCoord(co);

				pg.setType(type);
				map.addMapObject(pg);
				map.addPolygonOverview(new PolygonOverview(type));
			}
		}

		map.addPolygonOverview(new PolygonOverview(0));
	}
}