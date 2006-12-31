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

import uk.me.parabola.imgfmt.app.Coord;

import java.util.List;

/**
 * Represent a line on a Garmin map.  Lines are a list of points.  They have
 * a type (major highway, stream etc) and a name.  And that is just about it.
 *
 * @author Steve Ratcliffe
 */
public class MapLine extends MapElement {
	private List<Coord> points;
	private boolean direction; // set if direction is important.

	public List<Coord> getPoints() {
		return points;
	}

	public void setPoints(List<Coord> points) {
		this.points = points;
	}

	public boolean isDirection() {
		return direction;
	}

	public void setDirection(boolean direction) {
		this.direction = direction;
	}
}
