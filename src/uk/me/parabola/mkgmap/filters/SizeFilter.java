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

	private final int size;

	private int minSize;
	private final boolean checkRouting;

	public SizeFilter(int s, boolean doRoads) {
		size = s;
		checkRouting = doRoads;
	}
	
	public void init(FilterConfig config) {
		minSize = size * (1<<config.getShift());
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

		if (!line.isSkipSizeFilter() || (checkRouting && line.isRoad()) == false){
			if (line.getBounds().getMaxDimension() < minSize){
				return;
			}
		}
		next.doFilter(line);
	}
}
