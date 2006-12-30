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


/**
 * This interface can be used by map sources to collect the information.
 * It consists of all the 'writable' methods on {@link MapDetails}.
 *
 * @author Steve Ratcliffe
 */
public interface MapCollector {

	/**
	 * Add the given point to the total bounds for the map.
	 *
	 * @param p The coordinates of the point to add.  The type here
	 * will change to Node.
	 */
	public void addToBounds(Coord p);

	/**
	 * Add a point to the map.
	 *
	 * @param point The point to add.
	 */
	public void addPoint(MapPoint point);

	/**
	 * Add a line to the map.
	 *
	 * @param line The line information.
	 */
	public void addLine(MapLine line);

	/**
	 * Add the given shape (polygon) to the map.  A shape is very similar to
	 * a line but they are separate because they need to be put in different
	 * sections in the output map.
	 *
	 * @param shape The polygon to add.
	 */
	public void addShape(MapShape shape);
}
