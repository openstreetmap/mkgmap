/*
 * Copyright (C) 2017 
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
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;

/**
 * This filter splits a polygon if any of the subsequent filters throws a {@link MustSplitException}.
 * This will not happen often.
 *
 * @author Gerd Petermann
 */
public class PolygonSplitIfNeededFilter extends PolygonSplitterBase implements MapFilter {

	public PolygonSplitIfNeededFilter() {
	}

	/**
	 *
	 * @param element A map element, only polygons will be processed.
	 * @param next	This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		assert element instanceof MapShape;
		MapShape shape = (MapShape) element;

		try {
			next.doFilter(shape);
		} catch (MustSplitException e) {
			List<MapShape> outputs = new ArrayList<MapShape>();
			split(shape, outputs); // split in half
			for (MapShape s : outputs) {
				doFilter(s, next); // recurse as components could still be too big
			}
		}
	}
}
