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
package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * Represent a OSM way in the 0.5 api.  A way consists of an ordered list of
 * nodes.
 *
 * @author Steve Ratcliffe
 */
public class Way extends Element {

	private final List<Coord> points = new ArrayList<Coord>();

	public Way(long id) {
		setId(id);
	}

	/**
	 * Get the points that make up the way.  We attempt to re-order the segments
	 * and return a list of points that traces the route of the way.
	 *
	 * @return A simple list of points that form a line.
	 */
	public List<Coord> getPoints() {
		return points;
	}

	public boolean isBoolTag(String s) {
		String val = getTag(s);
		if (val == null)
			return false;

		if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes") || val.equals("1"))
			return true;

		// Not yet supporting the possible -1 value, which I think is best
		// fixed by reversing the way.

		return false;
	}

	public void addPoint(Coord co) {
		points.add(co);
	}


	/**
	 * A simple representation of this way.
	 * @return A string with the name and start point
	 */
	public String toString() {
		if (points.isEmpty())
			return "Way: empty";

		Coord coord = points.get(0);
		StringBuilder sb = new StringBuilder();
		sb.append("WAY: " + getId() + " ");
		sb.append(getName());
		sb.append('(');
		sb.append(Utils.toDegrees(coord.getLatitude()));
		sb.append('/');
		sb.append(Utils.toDegrees(coord.getLongitude()));
		sb.append(')');
		sb.append(' ');
		sb.append(toTagString());
		return sb.toString();
	}
}