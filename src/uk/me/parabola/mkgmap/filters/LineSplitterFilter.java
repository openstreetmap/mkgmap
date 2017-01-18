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
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * A filter that ensures that a line does not exceed the allowed number of
 * points that a line can have. If the line is split, each part will have at
 * least 50% of allowed number of points to avoid that too small parts are
 * filtered later.
 *
 * @author Steve Ratcliffe
 * @author Gerd Petermann
 */
public class LineSplitterFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(LineSplitterFilter.class);
	
	// Not sure of the value, probably 255.  Say 250 here.
	public static final int MAX_POINTS_IN_LINE = 250;

	private int level;
	private boolean isRoutable;
	public void init(FilterConfig config) {
		this.level = config.getLevel();
		this.isRoutable = config.isRoutable();
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
		if (npoints <= MAX_POINTS_IN_LINE) {
			next.doFilter(element);
			return;
		}

		
		log.debug("line has too many points, splitting");
		if(line.isRoad() && level == 0 && isRoutable && log.isDebugEnabled())  {
			log.debug("Way " + ((MapRoad)line).getRoadDef() + " has more than "+ MAX_POINTS_IN_LINE + " points and is about to be split");
		} 
		
		boolean last = false;
		int wantedSize = (npoints < 2 * MAX_POINTS_IN_LINE) ? npoints/ 2 + 1: MAX_POINTS_IN_LINE;
		int pos = 0;
		while (true) {
			if (pos == 0)
				log.debug("saving first part");
			else if (!last)
				log.debug("saving next part");
			else 
				log.debug("saving final part");

			MapLine l = line.copy();
			l.setPoints(new ArrayList<>(points.subList(pos, pos + wantedSize)));
			if (wantedSize < MAX_POINTS_IN_LINE / 2)
				log.error("size?",npoints,pos,wantedSize);
			if (!last && line instanceof MapRoad)  
				((MapRoad)line).setSegmentsFollowing(true);
			next.doFilter(l);
			
			if (last)
				break;
			
			pos += wantedSize - 1; // we start with the last point of previous part
			int remaining = npoints - pos;
			
			// make sure that the last parts have enough points
			if (remaining <= MAX_POINTS_IN_LINE) {
				last = true;
				wantedSize = remaining;
			} else if (remaining < 2 * MAX_POINTS_IN_LINE)
				wantedSize = remaining / 2 + 1;
		}
	}
}
