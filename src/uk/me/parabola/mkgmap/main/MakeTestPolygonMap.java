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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Polygon;
import uk.me.parabola.imgfmt.app.PolygonOverview;
import uk.me.parabola.imgfmt.app.Subdivision;

/**
 * A test map that contains a grid of polygons.  Each polygon has a different
 * type id in sequence.  the name is also the type_id.
 *
 * This can be used to decode the type_id's.
 *
 * @author Steve Ratcliffe
 */
public class MakeTestPolygonMap extends AbstractTestMap {

	public static void main(String[] args)  {
		MakeTestPolygonMap tm = new MakeTestPolygonMap();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double lat, double lng) {
		drawPolygons(map, div, lat, lng);
	}

	protected void writeOverviews(Map map) {
		// nothing to do here
	}

	private void drawPolygons(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		double space = 0.002;
		double size = 0.001;

		div.startShapes();

		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 16; y++) {
				int type = x*16 + y;

				Polygon pg = div.createPolygon("0x" + Integer.toHexString(type));
				double baseLat = lat + y * space;
				double baseLong = lon + x * space;
				Coord co = new Coord(baseLat, baseLong);
				pg.addCoord(co);
				co = new Coord(baseLat + size, baseLong);
				pg.addCoord(co);
				co = new Coord(baseLat + size, baseLong + size);
				pg.addCoord(co);
				co = new Coord(baseLat, baseLong + size);
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
