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

import java.io.FileNotFoundException;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.Subdivision;
import uk.me.parabola.imgfmt.app.Zoom;
import uk.me.parabola.imgfmt.app.Polygon;
import uk.me.parabola.log.Logger;

/**
 * A test routine to make an artificial map.  Makes some lines, squares etc
 * to test out various features of the map.
 *
 * This is just a hand constructed test map used for testing.
 *
 * @author steve
 */
public class MakeTestMap {
	private static final Logger log = Logger.getLogger(MakeTestMap.class);

	public static void main(String[] args) throws FileNotFoundException {

		// Default to nowhere in particular.
		double lat = 51.724;
		double lng = 0.2487;

		// Arguments allow you to place the map where ever you wish.
		if (args.length > 1) {
			lat = Double.valueOf(args[0]);
			lng = Double.valueOf(args[1]);
		}

		log.debug("this is a test make map program. Center " + lat + '/' + lng);

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("OSM street map");

		Map map = Map.createMap("32860003", params);
		map.setPoiDisplayFlags(0);
		map.addInfo("Program released under the GPL");
		map.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");

		// There has to be (at least) two copyright messages or else the map
		// does not show up.  The second one will be displayed at startup,
		// although the conditions where that happens are not known.
		map.addCopyright("program licenced under GPL v2");

		// This one gets shown when you switch on, so put the actual
		// map copyright here.
		map.addCopyright("OpenStreetMap.org contributers.");

		Area area = new Area(lat, lng, lat + 0.05, lng + 0.05);
		map.setBounds(area);

		// There must always be an empty zoom level at the least detailed level.
		log.info("area " + area);
		log.info(" or " + lat + '/' + lng);

		Zoom z1 = map.createZoom(1, 24);
		Subdivision topdiv = map.topLevelSubdivision(area, z1);

		// Create a most detailed view
		Zoom z = map.createZoom(0, 24);
		Subdivision div = map.createSubdivision(topdiv, area, z);


		Overview ov = new Overview(0x2c, 1, 5);
		map.addPointOverview(ov);
		ov = new Overview(0x2f, 1, 0xb);
		map.addPointOverview(ov);
		ov = new Overview(0x2d, 1, 0x2);
		map.addPointOverview(ov);
		ov = new Overview(0x0, 1, 0x22);
		map.addPointOverview(ov);

//		ov = new Overview(6, 1);
//		map.addPolylineOverview(ov);

//		div.setHasPolylines(true);
//		div.setHasPolygons(true);
//		map.startDivision(div);

//		drawLines(map, div, lat, lng);
//		drawPolygons(map, div, lat, lng);

		map.close();
	}

	private static void drawPolygons(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		double soff = 0.002;

		map.startShapes();

		Polygon pg = map.createPolygon(div, "Field of dreams");
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

	private static void drawLines(Map map, Subdivision div, double slat, double slng) {

		map.startLines();


		double lat = slat + 0.002;
		double lon = slng + 0.002;
		double soff = 0.001;

		Polyline pl = map.createLine(div, "Not really Square");
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
		pl = map.createLine(div, "Diamond Road");
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
		pl = map.createLine(div, "Straight Street");
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
		pl = map.createLine(div, "Back Street");
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
		pl = map.createLine(div, "Long Lane");
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

}
