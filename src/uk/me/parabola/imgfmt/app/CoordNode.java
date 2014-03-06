/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 13-Jul-2008
 */
package uk.me.parabola.imgfmt.app;

/**
 * A coordinate that is known to be a routing node.  You can tell by the fact
 * that getId() returns != 0.
 * 
 * @author Steve Ratcliffe
 */
public class CoordNode extends Coord {
	private final long id;

	/**
	 * Construct from co-ordinates that are already in map-units.
	 *
	 * @param latitude The latitude in map units.
	 * @param longitude The longitude in map units.
	 * @param id The ID of this routing node.
	 * @param boundary This is a routing node on the boundary.
	 */
	public CoordNode(int latitude, int longitude, long id, boolean boundary) {
		super(latitude, longitude);
		this.id = id;
		setOnBoundary(boundary);
		preserved(true);
	}

	public CoordNode(Coord other, long id, boolean boundary){
		super(other);
		this.id = id;
		setOnBoundary(boundary);
		preserved(true);
		
	}
	public long getId() {
		return id;
	}
}
