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
 * Create date: 11-Sep-2007
 */
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Coord;

import java.util.List;

/**
 * @author Steve Ratcliffe
 */
interface Way extends Iterable<String> {
	/**
	 * Override to allow ref to be returned if no name is set.
	 * If both then the ref is in brackets after the name.
	 * @return The name that has been set for the way.
	 */
	public String getName();

	public String getTag(String key);

	/**
	 * Get the points that make up the way.  We attempt to re-order the segments
	 * and return a list of points that traces the route of the way.
	 *
	 * @return A simple list of points that form a line.
	 */
	public List<Coord> getPoints();

	public boolean isBoolTag(String s);
}
