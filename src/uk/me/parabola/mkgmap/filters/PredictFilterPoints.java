/*
 * Copyright (C) 2017.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * Author: Ticker Berkin
 * Create date: 27-Jan-2017
 */
package uk.me.parabola.mkgmap.filters;

import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;

/**
 * Not actually a real filter, but estimates the number of points that would be left in
 * a polygon or line after simple RemoveObsolete/RoundCoords filtering at a given resolution
 *
 * Possibly could be extended to predict the effect of straight lines and spike removal
 * but wanted it to be as simple as possible to start with.
 *
 * @author Ticker Berkin
 */
public class PredictFilterPoints {

	public static int predictedMaxNumPoints(List<Coord> points, int resolution, boolean checkPreserved) {

	    	// see RemoveObsoletePointsFilter, RoundCoordsFilter, ... for comments on preserved and resolution
		// %%% checkPreserved = config.getLevel() == 0 && config.isRoutable() && line.isRoad()){
	    
//		final int shift = 30 - resolution; // NB getting highPrec
//		final int half = 1 << (shift - 1); // 0.5 shifted
//		final int mask = ~((1 << shift) - 1); // to remove fraction bits
		final int shift = 24 - resolution; // best use same info as filters
		int half;
		int mask;
		if (shift == 0) {
			half = 0;
			mask = ~0;
		} else {
			half = 1 << (shift - 1); // 0.5 shifted
			mask = ~((1 << shift) - 1); // to remove fraction bits
		}

		int numPoints = 0;
		int lastLat = 0, lastLon = 0;
		for (Coord p : points) {
//			final int lat = (p.getHighPrecLat() + half) & mask;
//			final int lon = (p.getHighPrecLon() + half) & mask;
			final int lat = (p.getLatitude() + half) & mask;
			final int lon = (p.getLongitude() + half) & mask;
			if (numPoints == 0)
				numPoints = 1; // always have one/first point
			else {
				if (lat != lastLat || lon != lastLon ||
				    (checkPreserved && p instanceof CoordNode && p.preserved()))
					++numPoints;
			}
			lastLat = lat;
			lastLon = lon;
		}
		return numPoints; // true shapes will have >3 points, lines >1
	} // predictNumNumPoints

}
