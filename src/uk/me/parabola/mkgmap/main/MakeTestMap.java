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

import org.apache.log4j.Logger;
import uk.me.parabola.imgfmt.sys.FileSystem;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.LBLFile;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.RGNFile;
import uk.me.parabola.imgfmt.app.Subdivision;
import uk.me.parabola.imgfmt.app.TREFile;
import uk.me.parabola.imgfmt.app.Zoom;

import java.io.FileNotFoundException;

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

		if (args.length > 1) {
			lat = Double.valueOf(args[0]);
			lng = Double.valueOf(args[1]);
		}

		log.debug("this is a test make map program. Center " + lat + '/' + lng);

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("This is my map");
		
		FileSystem fs = new FileSystem("gmapsupp.img", params);

		Map mp = Map.createMap(fs, "32860003");

		TREFile tf = mp.getTRE();
		RGNFile rgn = mp.getRGN();
		LBLFile lbl = mp.getLBL();

		Area area = new Area(lat, lng, lat + 0.05, lng + 0.05);

		tf.setBounds(area);
		tf.addInfo("Hello world");
		tf.addInfo("Hello another world");

		// There must always be an empty zoom level at the least detailed level.
		log.info("area " + area);
		log.info(" or " + lat + '/' + lng);

		Zoom z1 = tf.createZoom(1, 24);
		z1.setInherited(true);
		Subdivision div = Subdivision.topLevelSubdivision(area, z1);
		rgn.addDivision(div);

		tf.setBounds(area);
		
		// Create a most detailed view
		Zoom z = tf.createZoom(0, 24);
		div = div.createSubdivision(area, z);
		rgn.addDivision(div);

		Overview ov = new Overview(6, 1);
		tf.addPolylineOverview(ov);

		Coord co;
		div.setHasPolylines(true);

		// Draw nearly a square to test all directions.
		Polyline pl = new Polyline(div, 6); // residential road

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

		Label name = lbl.newLabel("Not Really Square");
		pl.setLabel(name);
		rgn.addMapObject(pl);

		// diagonal lines.
		pl = new Polyline(div, 6);
		lng += 0.004;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat + soff, lng + soff);
		pl.addCoord(co);
		co = new Coord(lat, lng+2*soff);
		pl.addCoord(co);
		co = new Coord(lat - soff, lng + soff);
		pl.addCoord(co);

		name = lbl.newLabel("Diamond Road");
		pl.setLabel(name);
		rgn.addMapObject(pl);

		// lines all in the same direction.
		pl = new Polyline(div, 6);
		lng += 0.006;
		double fine = soff/4;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat+soff+fine, lng+soff);
		pl.addCoord(co);
		co = new Coord(lat+2*soff, lng+ soff + fine);
		pl.addCoord(co);

		name = lbl.newLabel("Straight Street");
		pl.setLabel(name);
		rgn.addMapObject(pl);

		// Same but down to the left
		pl = new Polyline(div, 6);
		lng += 0.006;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat-soff-fine, lng-soff);
		pl.addCoord(co);
		co = new Coord(lat-2*soff, lng - soff - fine);
		pl.addCoord(co);

		name = lbl.newLabel("Back Street");
		pl.setLabel(name);
		rgn.addMapObject(pl);

		// A long street
		pl = new Polyline(div, 6);
		lng += 0.006;
		co = new Coord(lat, lng);
		pl.addCoord(co);
		co = new Coord(lat + 2 * soff, lng + 2 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 5 * soff - fine, lng + 4 * soff + fine);
		pl.addCoord(co);
		co = new Coord(lat + 80 * soff - fine, lng + 80 * soff - fine);
		pl.addCoord(co);

		name = lbl.newLabel("Long Lane");
		pl.setLabel(name);
		rgn.addMapObject(pl);

		Label copyright = lbl.newLabel("Copyright Steve Ratcliffe");
		tf.addCopyright(copyright);

		copyright = lbl.newLabel("another copyright message, just because");
		tf.addCopyright(copyright);

		mp.close();
		fs.close();
		
	}
}
