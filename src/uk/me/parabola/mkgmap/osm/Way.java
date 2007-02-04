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

		Coord start = null, end = null;

		List<List<Segment>> all = reorderSegments();

		for (List<Segment> segs : all) {
			log.debug("== new sublist");
			boolean firstPoint = true;

			List<Coord> points = new ArrayList<Coord>();
			pointLists.add(points);

			for (Segment seg : segs) {
				if (firstPoint) {
					start = seg.getStart();
					end = seg.getEnd();
					points.add(start);
					points.add(end);
					firstPoint = false;
				} else {
					Coord cs = seg.getStart();
					Coord ce = seg.getEnd();
					if (cs.equals(end)) {
						// this is normal add the next point
						log.debug(getName(), ce);
						points.add(ce);
						end = ce;
					} else {
						// this can't happen if the re-ordering has worked.
						log.debug("BAD:", getName(), start, "to", end, cs, ce);
						assert false;
					}
				}
			}
		}

		return pointLists;
	}

	/**
	 * Re-order our segments so that they all join up head-to-tail as much
	 * as possible.  In particular for a simple way that has no branches (the
	 * vast majority of them) this routine should give a single list.
	 *
	 * When it is not possible to produce a single list, for example the way
	 * really has a gap in it or branches, then the smallest number of lists
	 * that are all correctly and contiguously ordered should be returned.
	 *
	 * @return A list of lists of segements.  In each sublist the segments
	 * join together.
	 */
	private List<List<Segment>> reorderSegments() {
		List<List<Segment>> all = new ArrayList<List<Segment>>(10);
		List<Segment> workList = new ArrayList<Segment>(20);
		boolean newList = true;

		Coord start = null;
		Coord end = null;
		for (Segment seg : segments) {
			Coord s = seg.getStart();
			Coord e = seg.getEnd();

			if (newList) {
				log.debug(getName(), "initial segment", seg);
				start = s;
				end = e;
				workList.add(seg);
				newList = false;
			} else if (s.equals(end)) {
				// The normal case, this segment joins to the previous one.
				log.debug("normal join of ", seg);
				end = e;
				workList.add(seg);
			} else if (e.equals(end)) {
				// This segment will fit if reversed.
				log.debug("reversed segment", seg);
				String ow = seg.getTag("one_way");
				if (ow != null && ow.equals("true")) {
					log.debug("but one-way so we should not change it");
					workList = startNewSeg(all, workList);
					workList.add(seg);
					newList = true;
				} else {
					Segment seg2 = new Segment(seg.getId(), e, s);
					workList.add(seg2);
					end = s;
				}
			} else if (e.equals(start)) {
				// fits at beginning
				log.debug("fitting segment at start ", seg);
				workList.add(0, seg);
				start = s;
			} else if (s.equals(start)) {
				// fits at start if reversed.
				log.debug("reversed segment fitting at start", seg);
				String ow = seg.getTag("one_way");
				if (ow != null && ow.equals("true")) {
					log.debug("but one-way so we should not change it");
					workList = startNewSeg(all, workList);
					workList.add(seg);
					newList = true;
				} else {
					Segment seg2 = new Segment(seg.getId(), e, s);
					workList.add(0, seg2);
					start = e;
				}
			} else {
				log.debug("else case, nothing applies", seg);
				workList = startNewSeg(all, workList);
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

	/**
	 * Helper to merge a work list into the previously collected lists.  The
	 * new list may:
	 * <ol>
	 * <li>Fit at the end of a previous list
	 * <li>Fit at the beginning of a previous list.
	 * <li>Neither, in which case we make it a separate list.
	 * </ol>
	 *
	 * TODO: possible that when adding a new list to the end of a previous list
	 * that it will allow the list to be simplified more.  For example if
	 * the new list fits 'between' two existing lists, the current code will
	 * just append to one of the lists and not also append the existing list
	 * and remove it from the list-of-lists.
	 *
	 * @param all  The list of all the lines that we are collecting.
	 * @param workList The new list that we are trying to fit in.
	 */
	private static void mergeList(List<List<Segment>> all, List<Segment> workList) {
		boolean found = false;
		for (List<Segment> list : all) {
			Segment s = list.get(0);
			Segment e = list.get(list.size()-1);
			Segment ws = workList.get(0);
			Segment we = workList.get(workList.size()-1);

			if (ws.getStart().equals(e.getEnd())) {
				// Work list fits on the end of a previous list.
				log.debug("list fits at end of previous ", e, " to ", ws);
				list.addAll(workList);
				found = true;
				break;
			} else if (we.getEnd().equals(s.getStart())) {
				// Work list fits at the beginning of a previous list.
				log.debug("list fits at start of previous ", we, " to ", s);
				list.addAll(0, workList);
				found = true;
				break;
			}
		}

		if (!found)
			all.add(workList);
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
}
