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
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

import java.util.ArrayList;
import java.util.List;

public class PreserveHorizontalAndVerticalLinesFilter implements MapFilter {

	int shift;

	public void init(FilterConfig config) {
		shift = config.getShift();
	}

	/**
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;

		if(shift != 0) {
			// preserve the end points of horizontal and vertical lines
			List<Coord> points = line.getPoints();
			Coord first = points.get(0);
			Coord prev = first;
			Coord last = first;
			for(int i = 1; i < points.size(); ++i) {
				last = points.get(i);
				if(last.getLatitude() == prev.getLatitude() ||
				   last.getLongitude() == prev.getLongitude()) {
					last.preserved(true);
					prev.preserved(true);
				}
				prev = last;
			}
			// if the way has the same point at each end, make sure
			// that if either is preserved, they both are
			if(first.equals(last) && first.preserved() != last.preserved()) {
				first.preserved(true);
				last.preserved(true);
			}
		}

		next.doFilter(line);
	}
}
