/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 01-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

import java.awt.*;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * Clip a polygon to the given bounding box.  This may result in more than
 * one polygon.
 *
 * @author Steve Ratcliffe
 */
public class PolygonClipper {

	/**
	 * Clip the input polygon to the given area.
	 * @param bbox The bounding box.
	 * @param coords The coords of the polygon.
	 * @return Return null if the polygon is already completly inside the
	 * bounding box.
	 */
	public static List<List<Coord>> clip(Area bbox, List<Coord> coords) {
		if (bbox == null)
			return null;

			// If all the points are inside the box then we just return null
		// to show that nothing was done and the line can be used.  This
		// is expected to be the normal case.
		boolean foundOutside = false;
		for (Coord co : coords) {
			if (!bbox.contains(co)) {
				foundOutside = true;
				break;
			}
		}
		if (!foundOutside)
			return null;

		// Convert to a awt polygon
		Polygon polygon = new Polygon();
		for (Coord co : coords)
			polygon.addPoint(co.getLongitude(), co.getLatitude());

		Polygon bounds = new Polygon();
		bounds.addPoint(bbox.getMinLong(), bbox.getMinLat());
		bounds.addPoint(bbox.getMinLong(), bbox.getMaxLat());
		bounds.addPoint(bbox.getMaxLong(), bbox.getMaxLat());
		bounds.addPoint(bbox.getMaxLong(), bbox.getMinLat());
		bounds.addPoint(bbox.getMinLong(), bbox.getMinLat());

		java.awt.geom.Area bbarea = new java.awt.geom.Area(bounds);
		java.awt.geom.Area shape = new java.awt.geom.Area(polygon);

		shape.intersect(bbarea);

		return areaToShapes(shape);
	}

	/**
	 * Convert the area back into {@link MapShape}s.  It is possible that the
	 * area is multiple discontiguous polygons, so you may append more than one
	 * shape to the output list.
	 *
	 * @param area The area to be converted.
	 */
	private static List<List<Coord>> areaToShapes(java.awt.geom.Area area) {
		List<List<Coord>> outputs = new ArrayList<List<Coord>>();
		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);

		List<Coord> coords = null;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			Coord co = new Coord(Math.round(res[1]), Math.round(res[0]));

			if (type == PathIterator.SEG_MOVETO) {
				// We get a move to at the beginning and if this area is actually
				// discontiguous we may get more than one, each one representing
				// the start of another polygon in the output.
				if (coords != null)
					outputs.add(coords);

				coords = new ArrayList<Coord>();
				coords.add(co);
			} else if (type == PathIterator.SEG_LINETO) {
				// Continuing with the path.
				assert coords != null;
				coords.add(co);
			} else if (type == PathIterator.SEG_CLOSE) {
				// The end of a polygon
				assert coords != null;
				coords.add(co);

				outputs.add(coords);
				coords = null;
			}
			pit.next();
		}
		return outputs;
	}
}
