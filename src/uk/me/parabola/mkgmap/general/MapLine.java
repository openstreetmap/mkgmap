/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 18-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * Represent a line on a Garmin map.  Lines are a list of points.  They have
 * a type (major highway, stream etc) and a name.  And that is just about it.
 *
 * @author Steve Ratcliffe
 */
public class MapLine extends MapElement {
	private static final Logger log = Logger.getLogger(MapLine.class);

	private List<Coord> points;
	private boolean direction; // set if direction is important.
	private boolean skipSizeFilter;
	private int minLat = Integer.MAX_VALUE;
	private int minLong = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLong = Integer.MIN_VALUE;

	public MapLine() {
	}

	public MapLine(MapLine orig) {
		super(orig);
		direction = orig.direction;
		skipSizeFilter = orig.skipSizeFilter;
		//roadDef = orig.roadDef;
	}

	public MapLine copy() {
		return new MapLine(this);
	}

	public List<Coord> getPoints() {
		return points;
	}

	public void setPoints(List<Coord> points) {
		if (this.points != null)
			log.warn("overwriting points");
		assert points != null : "trying to set null points";
		assert !points.isEmpty() : "trying to set points with zero length";

		this.points = points;
		testForConsecutivePoints(points);
	}
	
	public void testForConsecutivePoints(List<Coord> points) {
		Coord last = null;
		for (Coord co : points) {
			if (last != null && last.equals(co))
				log.info("Line " + getName() + " has consecutive identical points at " + co.toDegreeString() + " (discarding)");
			else {
				addToBounds(co);
				last = co;
			}
		}
	}

	public void insertPointsAtStart(List<Coord> additionalPoints) {
		testForConsecutivePoints(additionalPoints);
		points.get(0).preserved(true);
		points.addAll(0, additionalPoints);
		points.remove(additionalPoints.size()-1);	//End node exists now twice
	}

	public void insertPointsAtEnd(List<Coord> additionalPoints) {
		testForConsecutivePoints(additionalPoints);
		additionalPoints.get(0).preserved(true);
		points.remove(points.size()-1); 
		points.addAll(additionalPoints);
	}

	public boolean isDirection() {
		return direction;
	}

	public void setDirection(boolean direction) {
		this.direction = direction;
	}

	public boolean isRoad() {
		return false;
	}

	public boolean isSkipSizeFilter() {
		return skipSizeFilter;
	}

	public void setSkipSizeFilter(boolean skipSizeFilter) {
		this.skipSizeFilter = skipSizeFilter;
	}


	/**
	 * Get the mid-point of the bounding box for this element.  This is as good
	 * an indication of 'where the element is' as any.  Previously we just
	 * used the location of the first point which would lead to biases in
	 * allocating elements to subdivisions.
	 *
	 * @return The mid-point of the bounding box.
	 */
	public Coord getLocation() {
		return new Coord((minLat + maxLat) / 2, (minLong + maxLong) / 2);// high prec not needed
	}

	/**
	 * We build up the bounding box of this element by calling this routine.
	 *
	 * @param co The coordinate to add.
	 */
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

	/**
	 * Get the region that this element covers.
	 *
	 * @return The area that bounds this element.
	 */
	public Area getBounds() {
		return new Area(minLat, minLong, maxLat, maxLong);
	}

}
