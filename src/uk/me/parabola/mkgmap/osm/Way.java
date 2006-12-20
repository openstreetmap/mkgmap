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

import org.apache.log4j.Logger;

/**
 * Represent a OSM way.  A way consists of an ordered list of segments.  Its
 * quite possible for these to be non contiguous and we shall have to deal with
 * that.
 * 
 * @author Steve Ratcliffe
 */
class Way {
	private static final Logger log = Logger.getLogger(Way.class);

	private final Map<String, String> tags = new HashMap<String, String>();
	private final List<Segment> segments = new ArrayList<Segment>();
	private String name;

	/**
	 * Add a segment to the way.
	 *
	 * @param seg The segment to add.
	 */
	public void addSegment(Segment seg) {
		if (seg == null)
			return;

		segments.add(seg);
	}

	/**
	 * Add a tag to the way.  Some tags are recognised separately and saved in
	 * separate fields.
	 *
	 * @param key The tag name.
	 * @param val Its value.
	 */
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
		if (segments.isEmpty())
			return "Way: empty";
		
		Coord coord = segments.get(0).getStart();
		String ret = "WAY: "
				+ name
				+ ' '
				+ Utils.toDegrees(coord.getLatitude())
				+ '/'
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

	/**
	 * Get the points that make up the way.  We attempt to re-order the segments
	 * and return a list of points that traces the route of the way.
	 *
	 * @return A simple list of points that form a line.
	 */
	public List<List<Coord>> getPoints() {
		List<List<Coord>> points = segmentsToPoints();
		return points;
	}

	/**
	 * Unfortunately there are many ways that have mis-ordered segments that
	 * do not work when converting to a map.
	 *
	 * @return A list of points on a line.
	 */
	private List<List<Coord>> segmentsToPoints() {

		List<List<Coord>> pointLists = new ArrayList<List<Coord>>();
		List<Coord> points = new ArrayList<Coord>();
		pointLists.add(points);
		int npoints = 0;

		Coord start = null, end = null;

		for (Segment seg : segments) {
			if (npoints == 0) {
				start = seg.getStart();
				end = seg.getEnd();
				points.add(start);
				points.add(end);
				npoints += 2;
			} else {
				Coord cs = seg.getStart();
				Coord ce = seg.getEnd();
				if (cs.equals(end)) {
					// this is normal add the next point
					end = ce;
					points.add(end);
					npoints++;
				} else if (ce.equals(start)) {
					// segment appears reversed, add the start of the segment.
					log.warn("segment " + seg.getId() + " reversed");
					end = cs;
					points.add(end);
					npoints++;
				} else {
					// segment discontinuous, TODO: try to match with other segments.
					log.warn("segment " + seg.getId() + " disjoint");
					// Start a new set of points
					points = new ArrayList<Coord>();
					pointLists.add(points);

					points.add(cs);
					points.add(ce);
					npoints = 2;
				}
			}
		}

		return pointLists;
	}
}
