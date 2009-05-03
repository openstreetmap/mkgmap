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
 * Create date: Dec 1, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

/**
 * This is a filter that dismisses elements too small for the current resolution
 */
public class SizeFilter implements MapFilter {

	// Minsize==1 may cause small holes in QLandkarte, but does not at etrex!
	private static final int MIN_SIZE = 2;

	private int minSize;

	public void init(FilterConfig config) {
		int shift = config.getShift();
		if (shift <= 23)
			minSize = 0;
		else
			minSize = MIN_SIZE * (1<<shift);
	}

	/**
	 * This applies to both lines and polygons. 
	 * Elements too small for current resolution will be dropped.
	 *
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;

		// Drop things that are too small to get displayed
		if (line.getBounds().getMaxDimention() < minSize)
			return;

		next.doFilter(line);
	}
}
