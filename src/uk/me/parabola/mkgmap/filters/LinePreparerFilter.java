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

import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.LinePreparer;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * This filter does more or less the same calculations as LinePreparer.calcDeltas   
 * It rejects lines that have not enough different points, and it optimises
 * shapes so that they require fewer bits in the img file.
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
	 * @param next This is used to pass the element onward.
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
		// fields to keep track of the largest delta values  
		int[] maxBits = {0,0};
		int[] maxBits2nd = {0,0};
		int[] maxBitsPos = {0,0};
		
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
				if(!line.isRoad() || co.getId() == 0)
					continue;
			}
			++numPointsEncoded;
			if (numPointsEncoded >= minPointsRequired && element instanceof MapShape == false)
				break;
			// find out largest and 2nd largest delta for both dx and dy
			for (int k = 0; k < 2; k++){
				int nBits = LinePreparer.bitsNeeded((k==0) ? dx:dy);
				if (nBits > maxBits2nd[k]){
					if (nBits > maxBits[k]){
						maxBits2nd[k] = maxBits[k];
						maxBits[k] = nBits;
						maxBitsPos[k] = i;
					} 
					else
						maxBits2nd[k] = nBits;
				}
			}
			
		}		
		if(numPointsEncoded < minPointsRequired)
			return;
		if (minPointsRequired >= 3){
			// check if we can optimise shape by rotating
			// so that the line segment that requires the highest number of bits 
			// is not encoded and thus fewer bits 
			// are required for all points
			// TODO: maybe add additional points to further reduce max. delta values
			// or reverse order if largest delta is negative
			int maxReduction = 0;
			int rotation = 0;
			
			for (int k = 0; k < 2; k++){
				int delta = maxBits[k] - maxBits2nd[k]; 
				// prefer largest delta, then smallest rotation
				if (delta > maxReduction || delta == maxReduction && rotation > maxBitsPos[k]){
					maxReduction = delta;
					rotation = maxBitsPos[k];
				} 
			}
			/*
			int savedBits = (numPoints-1 * maxReduction);
			if (savedBits > 100){
				System.out.println("rotation of shape saves " + savedBits + " bits");
			}
			*/
			if (rotation != 0){
				List<Coord> points = line.getPoints();
				if (minPointsRequired == 4)
					points.remove(numPoints-1);
				Collections.rotate(points, -rotation);
				if (minPointsRequired == 4)
					points.add(points.get(0));
			}
		}
		next.doFilter(element);
	}
}
