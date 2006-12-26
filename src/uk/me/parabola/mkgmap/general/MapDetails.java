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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.Utils;

import java.util.List;
import java.util.ArrayList;

/**
 * The map features that we are going to map are collected here.
 * 
 * @author Steve Ratcliffe
 */
public class MapDetails implements MapCollector {
	private final List<MapLine> lines = new ArrayList<MapLine>();
	private final List<MapShape> shapes = new ArrayList<MapShape>();
	private final List<MapPoint> points = new ArrayList<MapPoint>();

    private int minLat = Utils.toMapUnit(180.0);
	private int minLon = Utils.toMapUnit(180.0);
	private int maxLat = Utils.toMapUnit(-180.0);
	private int maxLon = Utils.toMapUnit(-180.0);

	/**
	 * Add a line to the map.
	 *
	 * @param line The line information.
	 */
	public void addLine(MapLine line) {
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
        shapes.add(shape);
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

    public void addPoint(MapPoint point) {
        points.add(point);
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
}
