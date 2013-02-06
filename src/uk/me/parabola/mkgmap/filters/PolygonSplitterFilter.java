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

import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Split polygons so that they have less than the maximum number of points.
 * This is handled by using java built in classes.  Basically I am just taking
 * the bounding box, splitting that in half and getting the intersection of
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
public class PolygonSplitterFilter extends PolygonSplitterBase implements MapFilter {
	public static final int MAX_POINT_IN_ELEMENT = 250;

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
		for (MapShape s : outputs) {
			if (s == null)
				continue;
			next.doFilter(s);
		}
	}
}
