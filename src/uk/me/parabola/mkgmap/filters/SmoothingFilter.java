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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a filter that smooths out lines at low resolutions. If the element
 * has no size at all at the given resolution, then it is not passed on down
 * the chain at all is excluded from the map at that resolution.
 *
 * @author Steve Ratcliffe
 */
public class SmoothingFilter implements MapFilter {

	private static final int MIN_SPACING = 5;
	private static final int MIN_SIZE = 0;

	private int shift;

	public void init(FilterConfig config) {
		this.shift = config.getShift();
	}

	/**
	 * This applies to both lines and polygons.  We are going to smooth out
	 * the points in the line so that you do not get jaggies.  We are assuming
	 * that there is not an excess of points at the highest resolution.
	 *
	 * <ol>
	 * <li>If there is just one point, the drop it.
	 * <li>Ff the element is too small altogether, then drop it.
	 * <li>If there are just two points the pass it on unchanged.  This is
	 * probably a pretty common case.
	 * <li>The first point goes in unchanged.
	 * <li>Average points in groups so that they exceed the step size
	 * at the shifted resolution.
	 * </ol>
	 *
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;

		// First off we don't touch things if at the highest level of detail
		if (shift == 0) {
			next.doFilter(element);
			return;
		}

		// Drop things that are too small.
		if (line.getBounds().getMaxDimention() < MIN_SIZE)
			return;

		// If the line is not very long then just let it through.  This is done
		// mainly for the background polygons.
		List<Coord> points = line.getPoints();
		int n = points.size();
		if (n <= 5) {
			next.doFilter(element);
			return;
		}

		// Create a new list to rewrite the points into.
		List<Coord> coords = new ArrayList<Coord>(n);

		// Get the step size, we want to place a point every time the
		// average exceeds this size.
		int stepsize = MIN_SPACING << shift;

		// Always add the first point
		Coord last = points.get(0);
		coords.add(last);

		// Average the rest
		Average av = new Average(last, stepsize);
		for (int i = 1; i < n; i++) {
			Coord co = points.get(i);
			av.add(co);

			if (av.isMoreThanStep()) {
				Coord nco = av.getAverageCoord();
				coords.add(nco);
				if (av.pointCounter()>1) i--;

				last = nco;
				av.reset(last);
			}
		}

		Coord end = points.get(n - 1);
		if (!last.equals(end))
			coords.add(end);

		MapLine newelem;
		if (element instanceof MapShape)
			newelem = new MapShape(line);
		else
			newelem = new MapLine(line);

		newelem.setPoints(coords);
		next.doFilter(newelem);
	}

	/**
	 * Class for averaging out points that are close together.
	 */
	private static class Average {
		private int count;

		private int startLat;
		private int startLon;

		private int avlat;
		private int avlon;

		private int step;

		private final int stepsize;

		Average(Coord start, int stepsize) {
			this.startLat = start.getLatitude();
			this.startLon = start.getLongitude();
			this.stepsize = stepsize;
		}

		public void add(int lat, int lon) {
			count++;
			this.avlat += lat;
			this.avlon += lon;

			step += Math.abs(startLat - lat);
			step += Math.abs(startLon - lon);
		}

		public void reset(Coord start) {
			this.startLat = start.getLatitude();
			this.startLon = start.getLongitude();
			step = 0;
			count = 0;
			avlat = 0;
			avlon = 0;
		}

		public Coord getAverageCoord() {
			assert count > 0;
			return new Coord(avlat / count, avlon / count);
		}

		public void add(Coord co) {
			add(co.getLatitude(), co.getLongitude());
		}

		public boolean isMoreThanStep() {
			return (step > stepsize);
		}

		public int pointCounter() {
			return count;
		}
	}
}
