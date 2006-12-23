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

import org.apache.log4j.Logger;

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
		params.setMapDescription("This is my map");
		
		Map map = Map.createMap("32860003", params);

		Area area = new Area(lat, lng, lat + 0.05, lng + 0.05);

		map.addInfo("Hello world");
		map.addInfo("Hello another world");

		// There must always be an empty zoom level at the least detailed level.
		log.info("area " + area);
		log.info(" or " + lat + '/' + lng);

		Zoom z1 = map.createZoom(1, 24);
		Subdivision topdiv = map.topLevelSubdivision(area, z1);

		map.setBounds(area);
		
		// Create a most detailed view
		Zoom z = map.createZoom(0, 24);
		Subdivision div = map.createSubdivision(topdiv, area, z);

		Overview ov = new Overview(6, 1);
		map.addPolylineOverview(ov);

		Coord co;
		div.setHasPolylines(true);

		// Draw nearly a square to test all directions.
        Polyline pl = map.createLine(div, "Not really Square");

		lat += 0.002;
		lng += 0.002;
		double soff = 0.001;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat + soff, lng);
		pl.addCoord(co);
		co = new Coord(lat + soff, lng + soff);
		pl.addCoord(co);
		co = new Coord(lat, lng + soff);
		pl.addCoord(co);
		co = new Coord(lat, lng+soff/2);
		pl.addCoord(co);

		map.addMapObject(pl);

		// diagonal lines.
		pl = map.createLine(div, "Diamond Road");
		lng += 0.004;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat + soff, lng + soff);
		pl.addCoord(co);
		co = new Coord(lat, lng+2*soff);
		pl.addCoord(co);
		co = new Coord(lat - soff, lng + soff);
		pl.addCoord(co);

		map.addMapObject(pl);

		// lines all in the same direction.
		pl = map.createLine(div, "Straight Street");
		lng += 0.006;
		double fine = soff/4;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat+soff+fine, lng+soff);
		pl.addCoord(co);
		co = new Coord(lat+2*soff, lng+ soff + fine);
		pl.addCoord(co);

		map.addMapObject(pl);

		// Same but down to the left
		pl = map.createLine(div, "Back Street");
		lng += 0.006;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat-soff-fine, lng-soff);
		pl.addCoord(co);
		co = new Coord(lat-2*soff, lng - soff - fine);
		pl.addCoord(co);

		map.addMapObject(pl);

		// A long street
		pl = map.createLine(div, "Long Lane");
		lng += 0.006;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat + 2 * soff, lng + 2 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 5 * soff - fine, lng + 4 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 80 * soff - fine, lng + 80 * soff - fine);
		pl.addCoord(co);

		map.addMapObject(pl);

		map.addCopyright("Copyright blah blah");

		map.addCopyright("Another copyright message");

		map.close();
	}
}
