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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * A filter to make sure that a line does not have a greater dimension that
 * would cause an overflow of a TRE area which can only have 15 bits of
 * size.  We want to keep things well under this.
 *
 * @author Steve Ratcliffe
 */
public class LineSizeSplitterFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(LineSplitterFilter.class);

	// Half the max size.  Reduce further perhaps.
	private static final int MAX_SIZE = 0x7fff/2;

	public void init(FilterConfig config) {
	}

	/**
	 * Keep track of the max dimentions of a line and split when they get too
	 * big.
	 *
	 * @param element A map element.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		// We do not deal with shapes.
		assert !(element instanceof MapShape) && element instanceof MapLine;

		MapLine line = (MapLine) element;

		if (line.getBounds().getMaxDimention() < MAX_SIZE) {
			next.doFilter(element);
			return;
		}

		List<Coord> points = line.getPoints();

		log.debug("line too big, splitting");

		MapLine l = line.copy();

		List<Coord> coords = new ArrayList<Coord>();
		boolean first = true;

		/**
		 * Class to keep track of the dimentions.
		 */
		class Dim {
			private int minLat;
			private int minLong;
			private int maxLat;
			private int maxLong;

			Dim() {
				reset();
			}

			private void reset() {
				minLat = Integer.MAX_VALUE;
				minLong = Integer.MAX_VALUE;
				maxLat = Integer.MIN_VALUE;
				maxLong = Integer.MIN_VALUE;
			}

			private void addToBounds(Coord co) {
				int lat = co.getLatitude();
				if (lat < minLat)
					minLat = lat;
				if (lat > maxLat)
					maxLat = lat;

				int lon = co.getLongitude();
				if (lon < minLong)
					minLong = lon;
				if (lon > maxLong)
					maxLong = lon;
			}

			private int getMaxDim() {
				int dx = maxLong - minLong;
				int dy = maxLat - minLat;
				return Math.max(dx, dy);
			}
		}

		Dim dim = new Dim();

		// Add points until too big and then start again with a fresh line.
		for (Coord co : points) {
			coords.add(co);
			dim.addToBounds(co);
			if (dim.getMaxDim() > MAX_SIZE) {
				log.debug("bigness saving first part");
				l.setPoints(coords);

				if (first)
					next.doFilter(l);
				else
					next.addElement(l);

				l = line.copy();

				first = false;
				dim.reset();
				coords = new ArrayList<Coord>();
				coords.add(co);
			}
		}

		if (!coords.isEmpty()) {
			log.debug("bigness saving a final part");
			l.setPoints(coords);
			next.addElement(l);
		}
	}
}
