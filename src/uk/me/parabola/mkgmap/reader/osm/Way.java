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
import uk.me.parabola.log.Logger;

/**
 * Represent a OSM way in the 0.5 api.  A way consists of an ordered list of
 * nodes.
 *
 * @author Steve Ratcliffe
 */
public class Way extends Element {
	private static final Logger log = Logger.getLogger(Way.class);
	private final List<Coord> points;

	// This will be set if a way is read from an OSM file and the first node is the same node as the last
	// one in the way. This can be set to true even if there are missing nodes and so the nodes that we
	// have do not form a closed loop.
	// Note: this is not always set
	private boolean closedInOSM;

	// This is set to false if, we know that there are nodes missing from this way.
	// If you set this to false, then you *must* also set closed to the correct value.
	private boolean complete  = true;
	
	private boolean isViaWay;

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
		dup.copyTags(this);
		dup.closedInOSM = this.closedInOSM;
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
			return closedInOSM;

		return !points.isEmpty() && hasIdenticalEndPoints();
	}

	/**
	 * 
	 * @return true if the way is really closed in OSM,
	 * false if the way was created by mkgmap or read from polish
	 * input file (*.mp). 
	 * 
	 */
	public boolean isClosedInOSM() {
		return closedInOSM;
	}

	/**
	 *  
	 * @return Returns true if the first point in the way is identical to the last.
	 */
	public boolean hasIdenticalEndPoints() {
		return !points.isEmpty() && points.get(0) == points.get(points.size()-1);
	}

	/**
	 *  
	 * @return Returns true if the first point in the way is identical to the last.
	 */
	public boolean hasEqualEndPoints() {
		return !points.isEmpty() && points.get(0).equals(points.get(points.size()-1));
	}

	public void setClosedInOSM(boolean closed) {
		this.closedInOSM = closed;
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

	/**
	 * calculate weighted centre of way, using high precision
	 * @return
	 */
	public Coord getCofG() {
		int numPoints = points.size();
		if(numPoints < 1)
			return null;

		double lat = 0;
		double lon = 0;
		for(Coord p : points) {
			lat += (double)p.getHighPrecLat()/numPoints;
			lon += (double)p.getHighPrecLon()/numPoints;
		}
		return Coord.makeHighPrecCoord((int)Math.round(lat), (int)Math.round(lon));
	}

	public String kind() {
		return "way";
	}

	// returns true if the way is a closed polygon with a clockwise
	// direction
	public static boolean clockwise(List<Coord> points) {

		
		if(points.size() < 3 || !points.get(0).equals(points.get(points.size() - 1)))
			return false;
		if (points.get(0).highPrecEquals(points.get(points.size() - 1)) == false){
			log.error("Way.clockwise was called for way that is not closed in high precision");
		}
		
		long area = 0;
		Coord p1 = points.get(0);
		for(int i = 1; i < points.size(); ++i) {
			Coord p2 = points.get(i);
			area += ((long)p1.getHighPrecLon() * p2.getHighPrecLat() - 
					 (long)p2.getHighPrecLon() * p1.getHighPrecLat());
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
			thisPoly.addPoint(p.getHighPrecLon(), p.getHighPrecLat());
		for(Coord p : other.points)
			if(!thisPoly.contains(p.getHighPrecLon(), p.getHighPrecLat()))
				return false;
		return true;
	}

	public boolean isViaWay() {
		return isViaWay;
	}

	public void setViaWay(boolean isViaWay) {
		this.isViaWay = isViaWay;
	}
}
