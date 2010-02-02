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

import java.awt.Polygon;
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

	private final List<Coord> points;

	public Way(long id) {
		points = new ArrayList<Coord>();
		setId(id);
	}

	public Way(long id, List<Coord> points) {
		this.points = points;
		setId(id);
	}

	public Way copy() {
		Way dup = new Way(getId(), new ArrayList<Coord>(points));
		dup.setName(getName());
		dup.copyTags(this);
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
		if(points.size() == 0 || !co.equals(points.get(points.size() - 1)))
			points.add(co);
	}

	public void reverse() {
		int numPoints = points.size();
		for(int i = 0; i < numPoints/2; ++i) {
			Coord t = points.get(i);
			points.set(i, points.get(numPoints - 1 - i));
			points.set(numPoints - 1 - i, t);
		}
	}

	public boolean isClosed() {
		return !points.isEmpty() && points.get(0).equals(points.get(points.size()-1));
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

	public Coord getCofG() {
		int lat = 0;
		int lon = 0;
		int numPoints = points.size();
		if(numPoints < 1)
			return null;
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
	public boolean clockwise() {

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
		return area < 0;
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
