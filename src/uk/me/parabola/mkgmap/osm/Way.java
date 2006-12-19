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
import uk.me.parabola.imgfmt.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Represent a OSM way.  A way consists of an ordered list of segments.  Its
 * quite possible for these to be non contiguous and we shall have to deal with
 * that.
 * 
 * @author Steve Ratcliffe
 */
public class Way {
	private Map<String, String> tags = new HashMap<String, String>();
	private List<Coord> points = new ArrayList<Coord>();
	private int npoints;
	String name;

	public void addSegment(Segment seg) {
		if (seg == null)
			return;
		
		Coord start = seg.getStart();
		Coord end = seg.getEnd();

		if (npoints == 0) {
			points.add(start);
			points.add(end);
			npoints += 2;
		} else {
			points.add(end);
			npoints++;
		}
	}

	public void addTag(String key, String val) {
		if (key.equals("name")) {
			name = val;
		} else {
			tags.put(key, val);
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
		String ret = "WAY: "
				+ name
				+ " "
				+ Utils.toDegrees(coord.getLatitude())
				+ "/"
				+ Utils.toDegrees(coord.getLongitude())
				;
		return ret;
	}

	public String getName() {
		return name;
	}

	public String getTag(String key) {
		return tags.get(key);
	}

	public List<Coord> getPoints() {
		return points;
	}
}
