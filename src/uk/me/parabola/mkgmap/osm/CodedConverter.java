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
 * Create date: 20-Dec-2006
 */
package uk.me.parabola.mkgmap.osm;

import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.imgfmt.app.Coord;

import java.util.List;

import org.apache.log4j.Logger;

/**
 * Convert from OSM ways and points to Garmin lines, polygons and points.
 *
 * <p>Have renamed this class to indicate that it a hardcoded converter.  It
 * would be much better to read the conversions from a file I guess. So allow
 * for that possibility.
 * 
 * @author Steve Ratcliffe
 */
class CodedConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(CodedConverter.class);

	private final MapCollector mapper;

	public CodedConverter(MapCollector mapper) {
		this.mapper = mapper;
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 *
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 * 
	 * @param way The OSM way.
	 */
	public void processWay(Way way) {
		log.debug(way);

		String highway = way.getTag("highway");
		if (highway != null)
			processHighway(way, highway);

        String leisure = way.getTag("leisure");
        if (leisure != null)
            processLeisure(way, leisure);
	}

	private void processHighway(Way way, String highway) {
		String name = way.getName();

		int type;
		if (highway.equals("motorway")) {
			type = 1;
		} else if (highway.equals("trunk")) {
			type = 2;
		} else if (highway.equals("primary")) {
			type = 3;
		} else if (highway.equals("secondary")) {
			type = 4;
		} else if (highway.equals("footway")) {
			type = 0x16;
		} else {
			type = 6;
		}

		makeLines(way, name, type);
	}

	private void processLeisure(Way way, String s) {
		String name = way.getName();

		int type;
		if (s.equals("park")) {
			type = 0x17;
		} else {
			return;
		}
		makeShape(way, name, type);
	}

	private void makeLines(Way way, String name, int type) {
		List<List<Coord>> pointLists =  way.getPoints();
		for (List<Coord> points : pointLists) {
			MapLine line = new MapLine();
			line.setName(name);
			line.setPoints(points);
			line.setType(type);

			mapper.addLine(line);
		}
	}

	private void makeShape(Way way, String name, int type) {
		List<List<Coord>> pointLists =  way.getPoints();
		for (List<Coord> points : pointLists) {
			MapShape line = new MapShape();
			line.setName(name);
			line.setPoints(points);
			line.setType(type);

			mapper.addShape(line);
		}
	}
}
