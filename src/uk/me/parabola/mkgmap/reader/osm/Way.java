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

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
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

	private final List<Coord> points;

	// This will be set if a way is read from an OSM file and the first node is the same node as the last
	// one in the way. This can be set to true even if there are missing nodes and so the nodes that we
	// have do not form a closed loop.
	// Note: this is not always set, you must use isClosed()
	private boolean closed;

	// This is set to false if, we know that there are nodes missing from this way.
	// If you set this to false, then you *must* also set closed to the correct value.
	private boolean complete  = true;

	public Way(long id) {
		points = new ArrayList<Coord>(5);
		setId(id);
	}

	public Way(long id, List<Coord> points) {
		this.points = new ArrayList<Coord>(points);
		setId(id);
	}

	public Way copy() {
		Way dup = new Way(getId(), points);
		dup.setName(getName());
		dup.copyTags(this);
		dup.closed = this.closed;
		dup.complete = this.complete;
		return dup;
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

		return false;
	}

	public boolean isNotBoolTag(String s) {
		String val = getTag(s);
		if (val == null)
			return false;

		if (val.equalsIgnoreCase("false") || val.equalsIgnoreCase("no") || val.equals("0"))
			return true;

		return false;
	}

	public void addPoint(Coord co) {
		points.add(co);
	}

	public void addPointIfNotEqualToLastPoint(Coord co) {
		if(points.isEmpty() || !co.equals(points.get(points.size() - 1)))
			points.add(co);
	}

	public void reverse() {
		Collections.reverse(points);
	}

	/**
	 * Returns true if the way is really closed in OSM.
	 *
	 * Will return true even if the way is incomplete in the tile that we are reading, but the way is
	 * really closed in OSM.
	 *
	 * @return True if the way is really closed.
	 */
	public boolean isClosed() {
		if (!isComplete())
			return closed;

		return !points.isEmpty() && points.get(0).equals(points.get(points.size()-1));
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	public boolean isComplete() {
		return complete;
	}

	/**
	 * Set this to false if you know that the way does not have its complete set of nodes.
	 *
	 * If you do set this to false, then you must also call {@link #setClosed} to indicate if the way
	 * is really closed or not.
	 */
	public void setComplete(boolean complete) {
		this.complete = complete;
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
		sb.append("WAY: ").append(getId()).append(" ");
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

	public int hashCode() {
		return (int) getId();
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return getId() == ((Way) o).getId();
	}

	public Coord getCofG() {
		int numPoints = points.size();
		if(numPoints < 1)
			return null;

		int lat = 0;
		int lon = 0;
		for(Coord p : points) {
			lat += p.getLatitude();
			lon += p.getLongitude();
		}
		return new Coord((lat + numPoints / 2) / numPoints,
						 (lon + numPoints / 2) / numPoints);
	}

	public String kind() {
		return "way";
	}

	// returns true if the way is a closed polygon with a clockwise
	// direction
	public static boolean clockwise(List<Coord> points) {

		if(points.size() < 3 || !points.get(0).equals(points.get(points.size() - 1)))
			return false;

		long area = 0;
		Coord p1 = points.get(0);
		for(int i = 1; i < points.size(); ++i) {
			Coord p2 = points.get(i);
			area += ((long)p1.getLongitude() * p2.getLatitude() - 
					 (long)p2.getLongitude() * p1.getLatitude());
			p1 = p2;
		}

		// this test looks to be inverted but gives the expected result!
		// empty linear areas are defined as clockwise 
		return area <= 0;
	}

	// simplistic check to see if this way "contains" another - for
	// speed, all we do is check that all of the other way's points
	// are inside this way's polygon
	public boolean containsPointsOf(Way other) {
		Polygon thisPoly = new Polygon();
		for(Coord p : points)
			thisPoly.addPoint(p.getLongitude(), p.getLatitude());
		for(Coord p : other.points)
			if(!thisPoly.contains(p.getLongitude(), p.getLatitude()))
				return false;
		return true;
	}
}
