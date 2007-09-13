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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent a OSM way in the 0.5 api.  A way consists of an ordered list of
 * nodes.
 *
 * @author Steve Ratcliffe
 */
class Way5 extends Element implements Way {
	private static final Logger log = Logger.getLogger(Way5.class);

	private final List<Coord> points = new ArrayList<Coord>();

	/**
	 * Override to allow ref to be returned if no name is set.
	 * If both then the ref is in brackets after the name.
	 */
	public String getName() {
		String ref = getTag("ref");
		String name = super.getName();
		if (name == null) {
			return ref;
		} else if (ref != null) {
			StringBuffer ret = new StringBuffer(name);
			ret.append(" (");
			ret.append(ref);
			ret.append(')');
			return ret.toString();
		} else {
			return name;
		}
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
		sb.append("WAY: ");
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

	/**
	 * Get the points that make up the way.  We attempt to re-order the segments
	 * and return a list of points that traces the route of the way.
	 *
	 * @return A simple list of points that form a line.
	 */
	public List<List<Coord>> getPoints() {
		List<List<Coord>> ll = new ArrayList<List<Coord>>();
		ll.add(points);
		return ll;
	}

	public boolean getBoolTag(String s) {
		String val = getTag(s);
		if (val == null)
			return false;

		if (val.equalsIgnoreCase("true"))
			return true;
		if (val.equalsIgnoreCase("yes"))
			return true;

		// Not going to support the possible -1 value.

		return false;
	}


	public void addPoint(Coord co) {
		points.add(co);
	}
}