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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;

public class RoundCoordsFilter implements MapFilter {

	private int shift;
	private boolean checkRouting;

	public void init(FilterConfig config) {
		shift = config.getShift();
		checkRouting = config.getLevel() == 0 && config.isRoutable() == true;
		
	}

	/**
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;
		int half = 1 << (shift - 1);	// 0.5 shifted
		int mask = ~((1 << shift) - 1); // to remove fraction bits

		if(shift == 0) {
			// do nothing
			next.doFilter(line);
		}
		else {
			// round lat/lon values to nearest for shift
			List<Coord> newPoints = new ArrayList<Coord>(line.getPoints().size());
			Coord lastP = null;
			for(Coord p : line.getPoints()) {
				int lat = (p.getLatitude() + half) & mask;
				int lon = (p.getLongitude() + half) & mask;
				Coord newP;
				
				if(p instanceof CoordNode && checkRouting)
					newP = new CoordNode(lat, lon, p.getId(), p.getOnBoundary(), p.getOnCountryBorder());
				else
					newP = new Coord(lat, lon);
				newP.preserved(p.preserved());

				// only add the new point if it has different
				// coordinates to the last point or if it's a
				// CoordNode and the last point wasn't a CoordNode
				if(lastP == null ||
				   !lastP.equals(newP) ||
				   (newP instanceof CoordNode && !(lastP instanceof CoordNode))) {
					newPoints.add(newP);
					lastP = newP;
				}
				else if(newP.preserved()) {
					// this point is not going to be used because it
					// has the same (rounded) coordinates as the last
					// node but it has been marked as being "preserved" -
					// transfer that property to the previous point so
					// that it's not lost
					lastP.preserved(true);
				}
			}
			if(newPoints.size() > 1) {
				MapLine newLine = line.copy();
				newLine.setPoints(newPoints);
				next.doFilter(newLine);
			}
		}
	}
}
