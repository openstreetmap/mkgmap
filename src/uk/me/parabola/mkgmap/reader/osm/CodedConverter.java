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
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.List;

/**
 * Convert from OSM ways and points to Garmin lines, polygons and points.
 *
 * <p>Have renamed this class to indicate that it a hardcoded converter.  It
 * would be much better to read the conversions from a file I guess. So allow
 * for that possibility.
 *
 * @deprecated This was just used in the early days to get going.  You wouldn't
 * want to use it now.
 * 
 * @author Steve Ratcliffe
 */
class CodedConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(CodedConverter.class);

	private final MapCollector mapper;

	CodedConverter(MapCollector mapper) {
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
	public void convertWay(Way way) {
		log.debug(way);

		String highway = way.getTag("highway");
		if (highway != null)
			processHighway(way, highway);

		String leisure = way.getTag("leisure");
		if (leisure != null)
			processLeisure(way, leisure);

		String landuse = way.getTag("landuse");
		if (landuse != null)
			processLanduse(way, landuse);

		String natural = way.getTag("natural");
		if (natural != null)
			processNatural(way, natural);

		String railway = way.getTag("railway");
		if (railway != null)
			processRail(way, railway);
	}

	private void processRail(Way way, String s) {
		int type;
		String name = way.getName();
		if (s.equals("rail")) {
			type = 0x14;
		} else {
			return;
		}
		makeLines(way, name, type);
	}

	public void convertNode(Node node) {
		String amenity = node.getTag("amenity");
		if (amenity != null)
			processAmenity(node, amenity);
	}

	private void processNatural(Way way, String s) {
		String name = way.getName();

		int type = 0;
		if (s.equals("water")) {
			type = 0x3c;
		} else if (s.equals("wood")) {
			type = 0x50;
		}
		makeShape(way, name, type);
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
		} else if (highway.equals("unclassified") || highway.equals("residential")) {
			type = 6;
		} else {
			return;
		}

		makeLines(way, name, type);
	}

	private void processLeisure(Way way, String s) {
		String name = way.getName();

		int type;
		if (s.equals("park")) {
			type = 0x17;
		} else if (s.equals("pitch")) {
			type = 0x19;
		} else {
			return;
		}
		makeShape(way, name, type);
	}

	private void processLanduse(Way way, String s) {
		String name = way.getName();

		int type = 0;
		if (s.equals("cemetery") || s.equals("cemetary")) {
			type = 0x1a;
		} else if (s.equals("allotments")) {
			type = 0;
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

	private void processAmenity(Node node, String s) {
		int type;
		int subType;
		String name = node.getName();

		if (s.equals("pub")) {
			type = 0x2d;
			subType = 2;
		} else if (s.equals("place_of_worship")) {
			type = 0x64;
			subType = 4;
		} else if (s.equals("parking")) {
			type = 0x2f;
			subType = 0xb;
		} else if (s.equals("school")) {
			type = 0x2c;
			subType = 5;
		} else if (s.equals("post_office")) {
			type = 0x2f;
			subType = 5;
		} else if (s.equals("toilets")) {
			type = 0;
			subType = 0x22;
		} else {
			type = -1;
			subType = -1;
		}

		if (type >= 0) {
			makePoint(node, name, type, subType);
		}
	}

	private void makePoint(Node node, String name, int type, int subType) {
		MapPoint point = new MapPoint();

		point.setName(name);
		point.setLocation(node.getLocation());
		point.setType(type);
		point.setSubType(subType);

		mapper.addPoint(point);
	}
}
