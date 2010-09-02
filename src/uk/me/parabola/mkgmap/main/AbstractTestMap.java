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

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.app.trergn.Zoom;
import uk.me.parabola.log.Logger;

/**
 * Common code used for the test maps.  The test maps are programatically
 * constructed to contain examples of each type of point and such like.
 * 
 * @author Steve Ratcliffe
 */
public abstract class AbstractTestMap {
	private static final Logger log = Logger.getLogger(AbstractTestMap.class);

	protected void makeMap(String[] args) {
		// Default to nowhere in particular.
		double lat = 51.724;
		double lng = 0.2487;

		// Arguments allow you to place the map where ever you wish.
		if (args.length > 1) {
			lat = Double.valueOf(args[0]);
			lng = Double.valueOf(args[1]);
		}

		log.debug("this is a test make map program. Start", lat, '/', lng);

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("OSM street map");

		Map map;
		try {
			map = Map.createMap("32860003", ".", params, "32860003");
		} catch (FileExistsException e) {
			throw new ExitException("File exists already", e);
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not create or write file", e);
		}
		map.addInfo("Program released under the GPL");
		map.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");

		// There has to be (at least) two copyright messages or else the map
		// does not show up.  The second one will be displayed at startup,
		// although the conditions where that happens are not known.
		map.addCopyright("program licenced under GPL v2");

		// This one gets shown when you switch on, so put the actual
		// map copyright here.  This is made up data, so no copyright applies.
		map.addCopyright("No copyright");

		Area area = new Area(lat, lng, lat + 1, lng + 1);
		map.setBounds(area);

		// There must always be an empty zoom level at the least detailed level.
		log.info("area " + area);
		log.info(" or " + lat + '/' + lng);

		Zoom z1 = map.createZoom(1, 24);
		Subdivision topdiv = map.topLevelSubdivision(area, z1);

		// Create a most detailed view
		Zoom z = map.createZoom(0, 24);
		Subdivision div = map.createSubdivision(topdiv, area, z);

		div.startDivision();

		drawTestMap(map, div, lat, lng);

		map.close();
	}

	protected abstract void drawTestMap(Map map, Subdivision div, double lat, double lng);

}
