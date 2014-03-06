/*
 * Copyright (C) 2012.
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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * Filter for lines and shapes. Remove obsolete points on straight lines and spikes.
 * @author GerdP
 *
 */
public class RemoveObsoletePointsFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(RemoveObsoletePointsFilter.class);
	
	final Coord[] areaTest = new Coord[3];

	private boolean checkPreserved;
	public void init(FilterConfig config) {
		checkPreserved = config.getLevel() == 0 && config.isRoutable();
	}

	/**
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		MapLine line = (MapLine) element;
		List<Coord> points = line.getPoints();
		int numPoints = points.size();
		if (numPoints <= 1){
			return;
		}
		int requiredPoints = (line instanceof MapShape ) ? 4:2; 
		List<Coord> newPoints = new ArrayList<Coord>(numPoints);
		while (true){
			boolean removedSpike = false;
			numPoints = points.size();
			

			Coord lastP = points.get(0);
			newPoints.add(lastP);
			for(int i = 1; i < numPoints; i++) {
				Coord newP = points.get(i);
				int last = newPoints.size()-1;
				lastP = newPoints.get(last);
				if (lastP.equals(newP)){
					// only add the new point if it has different
					// coordinates to the last point or is preserved
					if (checkPreserved && line.isRoad()){
						if (newP.preserved() == false)
							continue;
						else if (lastP.preserved() == false){
							newPoints.set(last, newP); // replace last
						} 
					} else  
						continue;
				}
				if (newPoints.size() > 1) {
					switch (Utils.isStraight(newPoints.get(last-1), lastP, newP)){
					case Utils.STRICTLY_STRAIGHT:
						if (checkPreserved && lastP.preserved() && line.isRoad()){
							// keep it
						} else {
							log.debug("found three consecutive points on strictly straight line");
							newPoints.set(last, newP);
							continue;
						}
						break;
					case Utils.STRAIGHT_SPIKE:
						if (line instanceof MapShape){
							log.debug("removing spike");
							newPoints.remove(last);
							removedSpike = true;
							if (newPoints.get(last-1).equals(newP))
								continue;
						}
						break;
					default:
						break;
					}
				}

				newPoints.add(newP);
			}
			if (!removedSpike || newPoints.size() < requiredPoints)
				break;
			points = newPoints;
			newPoints = new ArrayList<Coord>(points.size());
		}
		if (line instanceof MapShape && newPoints.size() > 3){
			// check special case: shape starts with spike
			if (Utils.isStraight(newPoints.get(0), newPoints.get(1), newPoints.get(newPoints.size()-2)) == Utils.STRICTLY_STRAIGHT){
				newPoints.remove(0);
				newPoints.set(newPoints.size()-1, newPoints.get(0));
				if (newPoints.get(newPoints.size()-2).equals(newPoints.get(newPoints.size()-1)))
					newPoints.remove(newPoints.size()-1);
			}
		}
		
		if (newPoints.size() != line.getPoints().size()){
			if (line instanceof MapShape && newPoints.size() <= 3 || newPoints.size() <= 1)
				return;
			MapLine newLine = line.copy();
			newLine.setPoints(newPoints);
			next.doFilter(newLine);
		} else {
			// no need to create new object
			next.doFilter(line);
		}
	}
}
