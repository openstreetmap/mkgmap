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
import uk.me.parabola.log.Logger;

import java.util.List;
import java.util.ArrayList;

/**
 * Represent a OSM way.  A way consists of an ordered list of segments.  Its
 * quite possible for these to be non contiguous and we shall have to deal with
 * that.
 *
 * @author Steve Ratcliffe
 */
class Way extends Element {
	private static final Logger log = Logger.getLogger(Way.class);

	private final List<Segment> segments = new ArrayList<Segment>();

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
	 * A simple representation of this way.
	 * @return A string with the name and start point
	 */
	public String toString() {
		if (segments.isEmpty())
			return "Way: empty";

		Coord coord = segments.get(0).getStart();
		String ret = "WAY: "
				+ getName()
				+ ' '
				+ Utils.toDegrees(coord.getLatitude())
				+ '/'
				+ Utils.toDegrees(coord.getLongitude())
				;
		return ret;
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
	 * Convert the segments to a list of points.  If there is more than one
	 * straight line in the way, then there will be several lists of point
	 * lists.
	 *
	 * Unfortunately there are many ways that have mis-ordered segments. These
	 * have to be separated into continuous lines for working on the map.
	 *
	 * @return A list of points on a line.
	 */
	private List<List<Coord>> segmentsToPoints() {

		List<List<Coord>> pointLists = new ArrayList<List<Coord>>();

		Coord start, end;

		List<List<Segment>> all = reorderSegments();

		for (List<Segment> segs : all) {
			log.debug("== new sublist");
			start = null;
			end = null;

			List<Coord> points = new ArrayList<Coord>();
			pointLists.add(points);

			for (Segment seg : segs) {
				if (start == null) {
					start = seg.getStart();
					end = seg.getEnd();
					points.add(start);
					points.add(end);
				} else {
					Coord cs = seg.getStart();
					Coord ce = seg.getEnd();
					if (cs.equals(end)) {
						// this is normal add the next point
						log.debug(this.getName() + ": " + ce);
						points.add(ce);
						end = ce;
					} else {
						log.debug("BAD:" + this.getName() + ":" + start + " to " + end + ", " + cs + " to " + ce);
						// can't happen because all is rearranged to avoid it.
						assert false;
					}
				}
			}
		}

		return pointLists;
	}

	private List<List<Segment>> reorderSegments() {
		List<List<Segment>> all = new ArrayList<List<Segment>>(10);
		List<Segment> workList = new ArrayList<Segment>(20);

		Coord start = null;
		Coord end = null;
		for (Segment seg : segments) {
			Coord s = seg.getStart();
			Coord e = seg.getEnd();

			if (start == null || end == null) {
				start = s;
				end = e;
				log.debug(getName() + ":initial segment " + seg);
				workList.add(seg);
			} else if (s.equals(end)) {
				// The normal case, this segment joins to the previous one.
				log.debug("normal join of " + seg);
				end = e;
				workList.add(seg);
			} else if (e.equals(end)) {
				// This segment will fit if reversed.
				log.debug("reversed segment");
				String ow = seg.getTag("one_way");
				if (ow != null && ow.equals("true")) {
					log.debug("but one-way so we should not change it");
					workList = startNewSeg(all, workList);
					workList.add(seg);
					end = null;
				} else {
					Segment seg2 = new Segment(seg.getId(), e, s);
					workList.add(seg2);
					end = s;
				}
			} else if (e.equals(start)) {
				// fits at beginning
				log.debug("fitting segment at start " + seg);
				workList.add(0, seg);
				start = s;
			} else if (s.equals(start)) {
				// fits at start if reversed.
				log.debug("reversed segment fitting at start");
				String ow = seg.getTag("one_way");
				if (ow != null && ow.equals("true")) {
					log.debug("but one-way so we should not change it");
					workList = startNewSeg(all, workList);
					workList.add(seg);
					end = null;
				} else {
					Segment seg2 = new Segment(seg.getId(), e, s);
					workList.add(0, seg2);
					start = e;
				}
			} else {
				log.debug("else case, nothing applies");
				workList = startNewSeg(all, workList);
				log.debug("expect zero " + workList.size() + ", new list at " + seg);
				workList.add(seg);
				start = s;
				end = e;
			}
		}

		mergeList(all, workList);
		return all;
	}

	private static List<Segment> startNewSeg(List<List<Segment>> all, List<Segment> workList) {
		mergeList(all, workList);

		return new ArrayList<Segment>(20);
	}

	private static void mergeList(List<List<Segment>> all, List<Segment> workList) {
		boolean found = false;
		for (List<Segment> list : all) {
			Segment s = list.get(0);
			Segment e = list.get(list.size()-1);
			Segment ws = workList.get(0);
			Segment we = workList.get(workList.size()-1);

			if (ws.getStart().equals(e.getEnd())) {
				// Work list fits on the end of a previous list.
				log.debug("list fits at end of previous " + e + " to " + ws);
				list.addAll(workList);
				found = true;
				break;
			} else if (we.getEnd().equals(s.getStart())) {
				// Work list fits at the beginning of a previous list.
				log.debug("list fits at start of previous " + we + " to " + s);
				list.addAll(0, workList);
				found = true;
				break;
			}
		}

		if (!found)
			all.add(workList);
	}
}
