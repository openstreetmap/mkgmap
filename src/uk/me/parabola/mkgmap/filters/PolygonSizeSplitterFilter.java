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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Split polygons for physical size (rather than number of points).  The plan
 * here is simple, if its too big, then cut it in half.  As we alway cut the largest
 * dimention, then we will soon enough have cut it down to be small enough.
 *
 * @author Steve Ratcliffe
 */
public class PolygonSizeSplitterFilter extends PolygonSplitterBase implements MapFilter {
	private int shift;

	/**
	 * Get the scale factor so that we don't oversplit.
	 *
	 * @param config Configuration information, giving parameters of the map level
	 * that is being produced through this filter.
	 */
	public void init(FilterConfig config) {
		shift = config.getShift();
		if (shift > 15)
			shift = 16;
	}

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

		int maxSize = MAX_SIZE << shift;
		if (isSizeOk(shape, maxSize)) {
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
			if (!isSizeOk(s, maxSize)) {
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

	private boolean isSizeOk(MapShape shape, int maxSize) {
		int factor = 4;
		return shape.getBounds().getMaxDimention() < maxSize
				//&& shape.getBounds().getWidth() < bounds.getWidth() / factor
				//&& shape.getBounds().getHeight() < bounds.getHeight() / factor
				;
	}

}
