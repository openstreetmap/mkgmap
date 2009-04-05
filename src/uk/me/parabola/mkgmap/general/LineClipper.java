/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 30-Jun-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * Routine to clip a polyline to a given bounding box.
 * @author Steve Ratcliffe
 * @see <a href="http://www.skytopia.com/project/articles/compsci/clipping.html">A very clear explaination of the Liang-Barsky algorithm</a>
 */
public class LineClipper {

	/**
	 * Clips a polyline by the given bounding box.  This may produce several
	 * separate lines if the line meanders in and out of the box.
	 * This will work even if no point is actually inside the box.
	 * @param a The bounding area.
	 * @param coords A list of the points in the line.
	 * @return Returns null if the line is completely in the bounding box and
	 * this is expected to be the normal case.
	 * If clipping is needed then an array of point lists is returned.
	 */
	public static List<List<Coord>> clip(Area a, List<Coord> coords) {

		// If all the points are inside the box then we just return null
		// to show that nothing was done and the line can be used.  This
		// is expected to be the normal case.
		if (a == null || a.allInside(coords))
			return null;

		class LineCollector {
			private final List<List<Coord>> ret = new ArrayList<List<Coord>>(4);
			private List<Coord> currentLine;
			private Coord last;

			public void add(Coord[] segment) {
				if (segment == null) {
					currentLine = null;
				} else {
					// we start a new line if there isn't a current one, or if the first
					// point of the segment is not equal to the last one in the line.
					if (currentLine == null || !segment[0].equals(last)) {
						currentLine = new ArrayList<Coord>(5);
						currentLine.add(segment[0]);
						currentLine.add(segment[1]);
						ret.add(currentLine);
					} else {
						currentLine.add(segment[1]);
					}
					last = segment[1];
				}
			}

		}

		LineCollector seg = new LineCollector();

		// Step through each segment, clip it if necessary and create a list of
		// lines from it.
		for (int i = 0; i <= coords.size() - 2; i++) {
			Coord[] pair = {coords.get(i), coords.get(i+1)};
			Coord[] clippedPair = clip(a, pair);
			seg.add(clippedPair);
		}
		return seg.ret;
	}

	/**
	 * A straight forward implementation of the Liang-Barsky algorithm as described
	 * in the referenced web page.
	 * @param a The clipping area.
	 * @param ends The start and end of the line the contents of this will
	 * be changed if the line is clipped to contain the new start and end
	 * points.  A point that was inside the box will not be changed.
	 * @return An array of the new start and end points if any of the line is
	 * within the box.  If the line is wholy outside then null is returned.
	 * If a point is within the box then the same coordinate object will
	 * be returned as was passed in.
	 * @see <a href="http://www.skytopia.com/project/articles/compsci/clipping.html">Liang-Barsky algorithm</a>
	 */
	private static Coord[] clip(Area a, Coord[] ends) {
		assert ends.length == 2;

		int x0 = ends[0].getLongitude();
		int y0 = ends[0].getLatitude();

		int x1 = ends[1].getLongitude();
		int y1 = ends[1].getLatitude();

		int dx = x1 - x0;
		int dy = y1 - y0;

		double[] t = {0, 1};

		int p = -dx;
		int q = -(a.getMinLong() - x0);
		boolean scrap = checkSide(t, p, q);
		if (scrap) return null;

		p = dx;
		q = a.getMaxLong() - x0;
		scrap = checkSide(t, p, q);
		if (scrap) return null;

		p = -dy;
		q = -(a.getMinLat() - y0);
		scrap = checkSide(t, p, q);
		if (scrap) return null;

		p = dy;
		q = a.getMaxLat() - y0;
		scrap = checkSide(t, p, q);
		if (scrap) return null;

		assert t[0] >= 0;
		assert t[1] <= 1;

		double d = 0.5;
		if (t[0] > 0)
			ends[0] = new Coord((int) (y0 + t[0] * dy + d), (int) (x0 + t[0] * dx + d));

		if (t[1] < 1)
			ends[1] = new Coord((int)(y0 + t[1] * dy + d), (int) (x0 + t[1] * dx + d));
		return ends;
	}

	private static boolean checkSide(double[] t, double p, double q) {
		double r = q/p;

		if (p == 0) {
			if (q < 0)
				return true;
		} else if (p < 0) {
			if (r > t[1])
				return true;
			else if (r > t[0])
				t[0] = r;
		} else {
			if (r < t[0])
				return true;
			else if (r < t[1])
				t[1] = r;
		}
		return false;
	}
}
	