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
import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;

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
			log.debug("Way " + road.getRoadDef() + " has more than "+ MAX_POINTS_IN_LINE + " points and is about to be split");
		} 

		List<Coord> coords = new ArrayList<Coord>();
		int count = 0;
		int remaining = points.size();
		int wantedSize = (remaining < MAX_POINTS_IN_LINE + MIN_POINTS_IN_LINE) ? remaining / 2 + 10 : MAX_POINTS_IN_LINE;
		List<List<Coord>>parts = new ArrayList<List<Coord>>();
		for (Coord co : points) {
			coords.add(co);
			--remaining;
			
			if (++count >= wantedSize) {
				parts.add(coords);
				count = 0;
				coords = new ArrayList<Coord>();
				coords.add(co);
				// make sure that the last part has at least 50 points
				if (remaining > MAX_POINTS_IN_LINE && remaining < MAX_POINTS_IN_LINE + MIN_POINTS_IN_LINE)
					wantedSize = remaining / 2 + 10;
			}
		}

		if (count != 0) {
			parts.add(coords);
		}
		List<MapLine> lines = new ArrayList<MapLine>();
		MapLine l = line;
		for (int i = 0; i < parts.size();i++){
			log.debug("max points limit: saving part " + (i+1));
			l = l.copy();
			if (i > 0 && l instanceof MapRoad){
				MapRoad road = (MapRoad) l;
				long prevSplitId = road.getSplitId();
				road.setSplitId(-1 * FakeIdGenerator.makeFakeId());
				road.linkWithPred(prevSplitId);
			}
			l.setPoints(parts.get(i));
			// first collect all lines to make sure that road segments
			// are properly linked
			lines.add(l);
		}
		for (MapLine mapLine: lines)
			next.doFilter(mapLine);
	}
}
