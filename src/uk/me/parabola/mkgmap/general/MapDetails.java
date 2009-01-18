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
 * Create date: 18-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.imgfmt.app.trergn.PointOverview;
import uk.me.parabola.imgfmt.app.trergn.PolygonOverview;
import uk.me.parabola.imgfmt.app.trergn.PolylineOverview;

/**
 * The map features that we are going to map are collected here.
 *
 * @author Steve Ratcliffe
 */
public class MapDetails implements MapCollector, MapDataSource {
	private final List<MapLine> lines = new ArrayList<MapLine>();
	private final List<MapShape> shapes = new ArrayList<MapShape>();
	private final List<MapPoint> points = new ArrayList<MapPoint>();

	private int minLat = Utils.toMapUnit(180.0);
	private int minLon = Utils.toMapUnit(180.0);
	private int maxLat = Utils.toMapUnit(-180.0);
	private int maxLon = Utils.toMapUnit(-180.0);

	// Keep lists of all items that were used.
	private final Map<Integer, Integer> pointOverviews = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> lineOverviews = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> shapeOverviews = new HashMap<Integer, Integer>();

	/**
	 * Add a point to the map.
	 *
	 * @param point Point to add.
	 */
	public void addPoint(MapPoint point) {
		updateOverview(pointOverviews, point.getType(), point.getMinResolution());

		points.add(point);
	}

	/**
	 * Add a line to the map.
	 *
	 * @param line The line information.
	 */
	public void addLine(MapLine line) {
		assert !(line instanceof MapShape);
		if (line.getPoints().isEmpty())
			return;

		updateOverview(lineOverviews, makeMapType(line.getType(), 0),
				line.getMinResolution());

		lines.add(line);
	}

	/**
	 * Add the given shape (polygon) to the map.  A shape is very similar to a
	 * line but they are separate because they need to be put in different
	 * sections in the output map.
	 *
	 * @param shape The polygon to add.
	 */
	public void addShape(MapShape shape) {
		if (shape.getPoints().isEmpty())
			return;

		int type = shape.getType();
		if (type != 0x4b)
			updateOverview(shapeOverviews, makeMapType(shape.getType(), 0),
					shape.getMinResolution());

		shapes.add(shape);
	}

	public void finish() {
	}

	/**
	 * Add the given point to the total bounds for the map.
	 *
	 * @param p The coordinates of the point to add.
	 */
	public void addToBounds(Coord p) {
		int lat = p.getLatitude();
		int lon = p.getLongitude();
		if (lat < minLat)
			minLat = lat;
		if (lat > maxLat)
			maxLat = lat;
		if (lon < minLon)
			minLon = lon;
		if (lon > maxLon)
			maxLon = lon;
	}

	/**
	 * Get the bounds of this map.
	 *
	 * @return An area covering all the points in the map.
	 */
	public Area getBounds() {
		return new Area(minLat, minLon, maxLat, maxLon);
	}

	public List<MapPoint> getPoints() {
		return points;
	}

	/**
	 * Get all the lines for this map.
	 *
	 * @return A list of all lines defined for this map.
	 */
	public List<MapLine> getLines() {
		return lines;
	}

	public List<MapShape> getShapes() {
		return shapes;
	}

	/**
	 * Get the overviews.  We construct them at this point from the information
	 * that we have built up.
	 * Perhaps this could be a separate class rather than a list.
	 * 
	 * @return A list of overviews.
	 */
	public List<Overview> getOverviews() {
		List<Overview> ovlist = new ArrayList<Overview>();

		for (Map.Entry<Integer, Integer> ent : pointOverviews.entrySet()) {
			Overview ov = new PointOverview(ent.getKey(), ent.getValue());
			ovlist.add(ov);
		}

		for (Map.Entry<Integer, Integer> ent : lineOverviews.entrySet()) {
			Overview ov = new PolylineOverview(ent.getKey(), ent.getValue());
			ovlist.add(ov);
		}

		for (Map.Entry<Integer, Integer> ent : shapeOverviews.entrySet()) {
			Overview ov = new PolygonOverview(ent.getKey(), ent.getValue());
			ovlist.add(ov);
		}
		
		return ovlist;
	}

	private int makeMapType(int type, int subtype) {
		return (type << 8) + (subtype & 0xff);
	}

	private void updateOverview(Map<Integer, Integer> overviews, int type, int minResolution) {
		Integer prev = overviews.get(type);
		if (prev == null)
			prev = 24;

		if (minResolution < prev)
			overviews.put(type, minResolution);
	}
}
