/*
 * Copyright (C) 2013.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */ 
 
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * This filter does more or less the same calculations as LinePreparer.calcDeltas   
 * It rejects lines that have not enough different points
 * @author GerdP
 *
 */
public class LinePreparerFilter implements MapFilter {

	private int shift;
	private final Subdivision subdiv;

	public LinePreparerFilter(Subdivision subdiv) {
		this.subdiv = subdiv;
	}

	public void init(FilterConfig config) {
		shift = config.getShift();
	}

	/**
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the (unchanged) element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;

		int numPoints = line.getPoints().size();
		boolean first = true;
		int minPointsRequired = (element instanceof MapShape) ? 3:2;
		if (minPointsRequired == 3 && line.getPoints().get(0).equals(line.getPoints().get(numPoints-1)))
			++minPointsRequired;

		int lastLat = 0;
		int lastLong = 0;
		int numPointsEncoded = 1;
		for (int i = 0; i < numPoints; i++) {
			Coord co = line.getPoints().get(i);

			int lat = subdiv.roundLatToLocalShifted(co.getLatitude());
			int lon = subdiv.roundLonToLocalShifted(co.getLongitude());
			
			if (first) {
				lastLat = lat;
				lastLong = lon;
				first = false;
				continue;
			}

			// compute normalized differences
			//   -2^(shift-1) <= dx, dy < 2^(shift-1)
			// XXX: relies on the fact that java integers are 32 bit signed
			final int offset = 8+shift;
			int dx = (lon - lastLong) << offset >> offset;
			int dy = (lat - lastLat) << offset >> offset;
			lastLong = lon;
			lastLat = lat;
			if (dx == 0 && dy == 0){
				continue;
			}
				
			++numPointsEncoded;
			if (numPointsEncoded >= minPointsRequired)
				break;
		}		
		if(numPointsEncoded < minPointsRequired)
			return;
		
		next.doFilter(element);
	}
}
