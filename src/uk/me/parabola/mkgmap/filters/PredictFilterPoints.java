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

/**
 * Not actually a real filter, but estimates the number of points that would be left in
 * a polygon or line after simple RemoveObsolete/RoundCoords filtering at a given resolution
 *
 * Possibly could be extended to predict the effect of straight lines and spike removal
 * but wanted it to be as simple as possible to start with.
 *
 * %%% need to handle checkPreserved when used for lines and decide on closing count
 *
 * @author Ticker Berkin
 */
public class PredictFilterPoints {


	public static int predictedMaxNumPoints(List<Coord> points, int resolution, boolean checkPreserved) {

		// %%% checkPreserved = config.getLevel() == 0 && config.isRoutable();
		final int shift = 30 - resolution; // NB getting highPrec
		final int half = 1 << (shift - 1); // 0.5 shifted
		final int mask = ~((1 << shift) - 1); // to remove fraction bits

		int numPoints = 0;
		int lastLat = 0, lastLon = 0;
		for (Coord p : points) {
			final int lat = (p.getHighPrecLat() + half) & mask;
			final int lon = (p.getHighPrecLon() + half) & mask;
			if (numPoints == 0)
				numPoints = 1; // always have the first point
			else {
//				if (checkRouting && p instanceof CoordNode && p.preserved()) { %%% }
				if (lat != lastLat || lon != lastLon)
					++numPoints;
			}
			lastLat = lat;
			lastLon = lon;
		}
// ??? what about correct closing if ness poly/vs line
	return numPoints;
	} // predictNumNumPoints
}
