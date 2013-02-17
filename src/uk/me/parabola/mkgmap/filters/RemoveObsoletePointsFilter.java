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
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * Filter for shapes. Remove obsolete points on straight lines and spikes.
 * @author GerdP
 *
 */
public class RemoveObsoletePointsFilter implements MapFilter {
	private static final Logger log = Logger.getLogger(RemoveObsoletePointsFilter.class);
	
	final Coord[] areaTest = new Coord[3];

	public void init(FilterConfig config) {
	}

	/**
	 * @param element A map element that will be a line or a polygon.
	 * @param next This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		if(element instanceof MapShape == false) {
			// do nothing
			next.doFilter(element);
			return;
		}
		MapShape shape = (MapShape) element;
		int numPoints = shape.getPoints().size();
		if (numPoints <= 3){
			// too few points for a shape
			return;
		}
		
		List<Coord> newPoints = new ArrayList<Coord>(numPoints);
		
		Coord lastP = null;
		
		for(int i = 0; i < numPoints; i++) {
			Coord newP = shape.getPoints().get(i);
			// only add the new point if it has different
			// coordinates to the last point 
			if(lastP == null ||!lastP.equals(newP)) {
				if (newPoints.size() > 1) {
					if (newPoints.get(newPoints.size()-2).equals(newP)){
						// detected simple spike
						log.debug("removing spike");
						newPoints.remove(newPoints.size()-1);
						lastP = newP;
						continue;
					}
					int last = newPoints.size()-1;
					areaTest[0] = newPoints.get(last-1);
					areaTest[1] = newPoints.get(last);
					areaTest[2] = newP;
					// calculate area that is enclosed the last two points and the new point
					long area = 0;
					Coord p1 = newP;
					for(int j = 0; j < 3; j++) {
						Coord p2 = areaTest[j];
						area += ((long)p1.getLongitude() * p2.getLatitude() - 
								(long)p2.getLongitude() * p1.getLatitude());
						p1 = p2;
					}
					if (area == 0){
						log.debug("found three consecutive points on straight line");
						// area is empty-> points lie on a straight line
						newPoints.set(last, newP);
						lastP = newP;
						continue;
					}
				}

				newPoints.add(newP);
				lastP = newP;
			}
		}
		if (newPoints.size() != shape.getPoints().size()){
			if (newPoints.size() <= 3)
				return;
			MapLine newLine = shape.copy();
			newLine.setPoints(newPoints);
			next.doFilter(newLine);
		} else {
			// no need to create new object
			next.doFilter(shape);
		}
	}
}
