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

import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

public class PreserveHorizontalAndVerticalLinesFilter implements MapFilter {

	private int shift;

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
			// preserve the end points of horizontal and vertical lines that lie
			// on the bbox of the shape. 
			int minLat = line.getBounds().getMinLat();
			int maxLat = line.getBounds().getMaxLat();
			int minLon = line.getBounds().getMinLong();
			int maxLon = line.getBounds().getMaxLong();
			
			List<Coord> points = line.getPoints();
			Coord first = points.get(0);
			Coord prev = first;
			Coord last = first;
			for(int i = 1; i < points.size(); ++i) {
				last = points.get(i);
				if(last.getLatitude() == prev.getLatitude() && (last.getLatitude() == minLat || last.getLatitude() == maxLat) ||
				   last.getLongitude() == prev.getLongitude()&& (last.getLongitude() == minLon || last.getLongitude() == maxLon)){
					last.preserved(true);
					prev.preserved(true);
				}
				prev = last;
			}
		}

		next.doFilter(line);
	}
}
