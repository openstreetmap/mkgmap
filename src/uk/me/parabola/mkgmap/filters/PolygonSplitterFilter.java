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
 * Create date: Dec 2, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapShape;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Split polygons.  This is difficult and I don't know how to do this, so I
 * am making use of the java built in classes.  Basically I am just taking
 * the bounding box, spliting that in half and getting the intersection of
 * each half-box with the original shape.  Recurse until all are small enough.
 *
 * <p>Cutting things up may make discontiguous shapes, but this is handled by
 * the java classes (for sure) and my code (probably).
 *
 * <p>Written assuming that this is not very common, once we start doing sea
 * areas, may want to re-examine to see if we can optimize.
 * 
 * @author Steve Ratcliffe
 */
public class PolygonSplitterFilter extends BaseFilter implements MapFilter {
	private static final int MAX_POINT_IN_ELEMENT = 250;

	/**
	 * Split up polygons that have more than the max allowed number of points.
	 * Initially I shall just throw out polygons that have too many points
	 * to see if this is causing particular problems.
	 *
	 * @param element A map element, only polygons will be processed.
	 * @param next	This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		assert element instanceof MapShape;
		MapShape shape = (MapShape) element;

		int n = shape.getPoints().size();
		if (n < MAX_POINT_IN_ELEMENT) {
			// This is ok let it through and return.
			next.doFilter(element);
			return;
		}

		List<MapShape> outputs = new ArrayList<MapShape>();

		// Do an initial split
		split(shape, outputs);

		// Now check that all the resulting parts are also small enough.
		// NOTE: the end condition is changed from within the loop.
		for (int i = 0; i < outputs.size(); i++) {
			MapShape s = outputs.get(i);
			if (s.getPoints().size() > MAX_POINT_IN_ELEMENT) {
				// Not small enough, so remove it and split it again.  The resulting
				// pieces will be placed at the end of the list and will be
				// picked up later on.
				outputs.set(i, null);
				split(s, outputs);
			}
		}

		// Now add all to the chain.
		boolean first = true;
		for (MapShape s : outputs) {
			if (s == null)
				continue;
			if (first) {
				first = false;
				next.doFilter(s);
			} else
				next.addElement(s);
		}
	}

	/**
	 * Split the given shape and place the resulting shapes in the outputs list.
	 * @param shape The original shape (that is too big).
	 * @param outputs The output list.
	 */
	private void split(MapShape shape, List<MapShape> outputs) {
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
			r1 = new Rectangle(bounds.x, bounds.y, bounds.width / 2, bounds.height);
			r2 = new Rectangle(bounds.x + bounds.width/2, bounds.y, bounds.width / 2, bounds.height);
		} else {
			r1 = new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height/2);
			r2 = new Rectangle(bounds.x, bounds.y + bounds.height/2, bounds.width, bounds.height/2);
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
			Coord co = new Coord(round(res[1]), round(res[0]));

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
			}
			pit.next();
		}
	}

	private int round(float re) {
		return (int) (re + 0.999f);
	}

}
