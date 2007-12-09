/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 6, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.imgfmt.app.Coord;

import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;

/**
 * @author Steve Ratcliffe
 */
public class PolygonSplitterBase extends BaseFilter {
	public static final int MAX_SIZE = 0x7fff/2;

	/**
	 * Split the given shape and place the resulting shapes in the outputs list.
	 * @param shape The original shape (that is too big).
	 * @param outputs The output list.
	 */
	protected void split(MapShape shape, List<MapShape> outputs) {

		// Convert to a awt polygon
		Polygon polygon = new Polygon();
		for (Coord co : shape.getPoints()) {
			polygon.addPoint(co.getLongitude(), co.getLatitude());
		}

		// Get the bounds of this polygon
		Rectangle bounds = polygon.getBounds();

		if (bounds.isEmpty())
			return;  // Drop it

		// Cut the bounding box into two rectangles
		Rectangle r1;
		Rectangle r2;
		if (bounds.width > bounds.height) {
			int halfWidth = bounds.width / 2;
			r1 = new Rectangle(bounds.x, bounds.y, halfWidth, bounds.height);
			r2 = new Rectangle(bounds.x + halfWidth, bounds.y, bounds.width - halfWidth, bounds.height);
		} else {
			int halfHeight = bounds.height / 2;
			r1 = new Rectangle(bounds.x, bounds.y, bounds.width, halfHeight);
			r2 = new Rectangle(bounds.x, bounds.y + halfHeight, bounds.width, bounds.height - halfHeight);
		}

		// Now find the intersection of these two boxes with the original
		// polygon.  This will make two new areas, and each area will be one
		// (or more) polygons.
		Area a1 = new Area(polygon);
		Area a2 = (Area) a1.clone();
		a1.intersect(new Area(r1));
		a2.intersect(new Area(r2));

		areaToShapes(shape, a1, outputs);
		areaToShapes(shape, a2, outputs);
	}

	/**
	 * Convert the area back into {@link MapShape}s.  It is possible that the
	 * area is multiple discontiguous polygons, so you may append more than one
	 * shape to the output list.
	 *
	 * @param origShape The original shape, this is only used as a prototype to
	 * copy for the newly created shapes.
	 * @param area The area to be converted.
	 * @param outputs Used to hold output shapes.
	 */
	private void areaToShapes(MapShape origShape, Area area, List<MapShape> outputs) {
		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);

		List<Coord> coords = null;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			//System.out.println("T" + type + " " + res[0] + "," + res[1] + " " + res[2] + "," + res[3] + " " + res[4] + "," + res[5]);
			Coord co = new Coord(Math.round(res[1]), Math.round(res[0]));

			if (type == PathIterator.SEG_MOVETO) {
				// We get a moveto at the begining and if this area is actually
				// discontiguous we may get more than one, each one representing
				// the start of another polygon in the output.
				if (coords != null) {
					MapShape s2 = new MapShape(origShape);
					s2.setPoints(coords);
					outputs.add(s2);
				}
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

				MapShape s2 = new MapShape(origShape);
				s2.setPoints(coords);
				outputs.add(s2);
				coords = null;
			}
			pit.next();
		}
	}
}
