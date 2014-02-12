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
 * points that a line can have. If the line is split, the last part
 * will have at least 50 points to avoid that too small parts are filtered later.
 *
 * @author Steve Ratcliffe
 */
public class LineSplitterFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(LineSplitterFilter.class);
	
	// Not sure of the value, probably 255.  Say 250 here.
	public static final int MAX_POINTS_IN_LINE = 250;
	public static final int MIN_POINTS_IN_LINE = 50;

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
		if(line.isRoad() && level == 0 && isRoutable) {
			MapRoad road = ((MapRoad)line);
			log.info("Way", road.getRoadDef(),"has more than", MAX_POINTS_IN_LINE, " points and is about to be split");
		} 

		MapLine l = line.copy();

		List<Coord> coords = new ArrayList<Coord>();
		int count = 0;
		boolean first = true;
		int remaining = points.size();
		int wantedSize = (remaining < MAX_POINTS_IN_LINE + MIN_POINTS_IN_LINE) ? remaining / 2 + 10 : MAX_POINTS_IN_LINE;

		for (Coord co : points) {
			coords.add(co);
			--remaining;
			
			if (++count >= wantedSize) {
				if (first)
					log.debug("saving first part");
				else
					log.debug("saving next part");
				l.setPoints(coords);
				if (l instanceof MapRoad){
					((MapRoad)l).setSegmentsFollowing(true);
				}
				next.doFilter(l);

				l = line.copy();
				count = 0;
				first = false;
				coords = new ArrayList<Coord>();
				coords.add(co);
				// make sure that the last part has at least 50 points
				if (remaining > MAX_POINTS_IN_LINE && remaining < MAX_POINTS_IN_LINE + MIN_POINTS_IN_LINE)
					wantedSize = remaining / 2 + 10;
			}
		}

		if (count != 0) {
			log.debug("saving a final part");
			l.setPoints(coords);
			next.doFilter(l);
		}
	}
}
