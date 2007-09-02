/*
 * File: MakeMap.java
 * 
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
 * Create date: 26-Nov-2006
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.PointOverview;
import uk.me.parabola.imgfmt.app.Polygon;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.PolylineOverview;
import uk.me.parabola.imgfmt.app.Subdivision;

/**
 * A test routine to make an artificial map.  Makes some lines, squares etc
 * to test out various features of the map.
 *
 * This is just a hand constructed test map used for testing.
 *
 * @author steve
 */
public class MakeTestMap extends AbstractTestMap {

	public static void main(String[] args)  {
		MakeTestMap tm = new MakeTestMap();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double lat, double lng) {
		drawLines(map, div, lat, lng);
		drawPolygons(map, div, lat, lng);
		writeOverviews(map);
	}

	private void drawPolygons(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		double soff = 0.002;

		div.startShapes();

		Polygon pg = div.createPolygon("Field of dreams");
		Coord co = new Coord(lat, lon);
		pg.addCoord(co);
		co = new Coord(lat + soff, lon);
		pg.addCoord(co);
		co = new Coord(lat + soff, lon + soff);
		pg.addCoord(co);
		co = new Coord(lat, lon + soff);
		pg.addCoord(co);
		co = new Coord(lat, lon);
		pg.addCoord(co);

		pg.setType(0x17);
		map.addMapObject(pg);
	}

	private void drawLines(Map map, Subdivision div, double slat, double slng) {

		div.startLines();

		double lat = slat + 0.002;
		double lon = slng + 0.002;
		double soff = 0.001;

		Polyline pl = div.createLine("Not really Square");
		pl.setType(6);
		Coord co;// Draw nearly a square to test all directions.
		co = new Coord(lat, lon);
		pl.addCoord(co);
		co = new Coord(lat + soff, lon);
		pl.addCoord(co);
		co = new Coord(lat + soff, lon + soff);
		pl.addCoord(co);
		co = new Coord(lat, lon + soff);
		pl.addCoord(co);
		co = new Coord(lat, lon +soff/2);
		pl.addCoord(co);

		map.addMapObject(pl);

		// diagonal lines.
		pl = div.createLine("Diamond Road");
		pl.setType(6);
		lon += 0.004;
		co = new Coord(lat, lon);
		pl.addCoord(co);
		co = new Coord(lat + soff, lon + soff);
		pl.addCoord(co);
		co = new Coord(lat, lon+2*soff);
		pl.addCoord(co);
		co = new Coord(lat - soff, lon + soff);
		pl.addCoord(co);

		map.addMapObject(pl);

		// lines all in the same direction.
		pl = div.createLine("Straight Street");
		pl.setType(6);
		lon += 0.006;
		double fine = soff/4;
		co = new Coord(lat, lon);
		pl.addCoord(co);
		co = new Coord(lat+soff+fine, lon+soff);
		pl.addCoord(co);
		co = new Coord(lat+2*soff, lon+ soff + fine);
		pl.addCoord(co);

		map.addMapObject(pl);

		// Same but down to the left
		pl = div.createLine("Back Street");
		pl.setType(6);
		lon += 0.006;
		co = new Coord(lat, lon);
		pl.addCoord(co);
		co = new Coord(lat-soff-fine, lon-soff);
		pl.addCoord(co);
		co = new Coord(lat-2*soff, lon - soff - fine);
		pl.addCoord(co);

		map.addMapObject(pl);

		// A long street
		pl = div.createLine("Long Lane");
		pl.setType(6);
		lon += 0.006;
		co = new Coord(lat, lon);
		pl.addCoord(co);
		co = new Coord(lat + 2 * soff, lon + 2 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 5 * soff - fine, lon + 4 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 80 * soff - fine, lon + 80 * soff - fine);
		pl.addCoord(co);

		map.addMapObject(pl);
	}

	private void writeOverviews(Map map) {
		PointOverview pov = new PointOverview(0x2c, 5);
		map.addPointOverview(pov);
		pov = new PointOverview(0x2f, 0xb);
		map.addPointOverview(pov);
		pov = new PointOverview(0x2d, 0x2);
		map.addPointOverview(pov);
		pov = new PointOverview(0x0, 0x22);
		map.addPointOverview(pov);

		PolylineOverview lov = new PolylineOverview(6);
		map.addPolylineOverview(lov);
	}
}
