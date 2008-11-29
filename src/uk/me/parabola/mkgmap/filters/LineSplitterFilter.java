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
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A filter that ensures that a line does not exceed the allowed number of
 * points that a line can have.
 *
 * @author Steve Ratcliffe
 */
public class LineSplitterFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(LineSplitterFilter.class);
	
	// Not sure of the value, probably 255.  Say 250 here.
	private static final int MAX_POINTS_IN_LINE = 250;

	public void init(FilterConfig config) {
	}

	/**
	 * If the line is short enough then we just pass it on straight away.
	 * Otherwise we cut it into pieces that are short enough and hand them
	 * on.
	 *
	 * @param element A map element.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		// We do not deal with shapes.
		assert !(element instanceof MapShape) && element instanceof MapLine;
		
		MapLine line = (MapLine) element;

		List<Coord> points = line.getPoints();
		int npoints = points.size();
		if (npoints < MAX_POINTS_IN_LINE) {
			next.doFilter(element);
			return;
		}

		log.debug("line too long, splitting");

		MapLine l = (MapLine) line.copy();

		List<Coord> coords = new ArrayList<Coord>();
		int count = 0;
		boolean first = true;

		for (Coord co : points) {
			coords.add(co);
			if (++count >= MAX_POINTS_IN_LINE) {
				log.debug("saving first part");
				l.setPoints(coords);

				if (first)
					next.doFilter(l);
				else
					next.addElement(l);

				l = new MapLine(line);

				count = 0;
				first = false;
				coords = new ArrayList<Coord>();
				coords.add(co);
			}
		}

		if (count != 0) {
			log.debug("saving a final part");
			l.setPoints(coords);
			next.addElement(l);
		}
	}
}
