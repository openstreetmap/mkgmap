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
 * Create date: 17-Dec-2006
 */
package uk.me.parabola.mkgmap.osm;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * Holds OSM segment information.  A segment is an order list of two nodes. It
 * can have properties, but usually doesn't (or shouldn't).
 *
 * @author Steve Ratcliffe
 */
public class Segment {

	private Coord start;
	private Coord end;
	private long id;

	public Segment(long id, Coord start, Coord end) {
		this.start = start;
		this.end = end;
	}

	public Coord getStart() {
		return start;
	}

	public Coord getEnd() {
		return end;
	}

	/**
	 * Get startLat.
	 *
	 * @return startLat as double.
	 */
	public double getStartLat()
	{
	    return start.getLatitude();
	}
	
	/**
	 * Get endLat.
	 *
	 * @return endLat as double.
	 */
	public double getEndLat()
	{
	    return end.getLatitude();
	}
	
	/**
	 * Get startLon.
	 *
	 * @return startLon as double.
	 */
	public double getStartLon()
	{
	    return start.getLongitude();
	}
	
	/**
	 * Get endLon.
	 *
	 * @return endLon as double.
	 */
	public double getEndLon()
	{
	    return end.getLongitude();
	}

	public long getId() {
		return id;
	}
}
