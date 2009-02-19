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
 * Create date: 11-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.util.Formatter;

import uk.me.parabola.imgfmt.Utils;

/**
 * A point coordinate in unshifted map-units.
 * A map unit is 360/2^24 degrees.  In some places <i>shifted</i> coordinates
 * are used, which means that they are divided by some power of two to save
 * space in the file.
 *
 * You can create one of these with lat/long by calling the constructor with
 * double args.
 *
 * This is an immutable class.
 *
 * @author Steve Ratcliffe
 */
public class Coord implements Comparable {
	private final int latitude;
	private final int longitude;
	private int highwayCount; // number of highways that use this point

	/**
	 * Construct from co-ordinates that are already in map-units.
	 * @param latitude The latitude in map units.
	 * @param longitude The longitude in map units.
	 */
	public Coord(int latitude, int longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	/**
	 * Construct from regular latitude and longitude.
	 * @param latitude The latitude in degrees.
	 * @param longitude The longitude in degrees.
	 */
	public Coord(double latitude, double longitude) {
		this.latitude = Utils.toMapUnit(latitude);
		this.longitude = Utils.toMapUnit(longitude);
	}

	public int getLatitude() {
		return latitude;
	}

	public int getLongitude() {
		return longitude;
	}

	public long getId() {
		return 0;
	}

	public int getHighwayCount() {
		return highwayCount;
	}

	public void incHighwayCount() {
		++highwayCount;
	}

	public int hashCode() {
		return latitude+longitude;
	}

	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj.getClass() != getClass())
			return false;
		Coord other = (Coord) obj;
		return latitude == other.latitude && longitude == other.longitude;
	}

	/**
	 * Distance to other point in meters.
	 */
	public double distance(Coord other) {
		if (equals(other))
			return 0;

		double lat1 = Utils.toRadians(latitude);
		double lat2 = Utils.toRadians(other.getLatitude());
		double lon1 = Utils.toRadians(getLongitude());
		double lon2 = Utils.toRadians(other.getLongitude());

		double R = 6371000; // meters

		// cosine of great circle angle between points
		double cangle = Math.sin(lat1)*Math.sin(lat2) +
			        Math.cos(lat1)*Math.cos(lat2) * Math.cos(lon2-lon1);

		return Math.acos(cangle) * R;
  	}

	/**
	 * Sort lexicographically by longitude, then latitude.
	 *
	 * This ordering is used for sorting entries in NOD3.
	 */
	public int compareTo(Object o) {
		Coord other = (Coord) o;
		int clon = longitude - other.getLongitude();
		if (clon != 0)
			return clon;
		else
			return latitude - other.getLatitude();
	}			

	/**
	 * Returns a string representation of the object.
	 *
	 * @return a string representation of the object.
	 */
	public String toString() {
		return (latitude) + "/" + (longitude);
	}

	public String toDegreeString() {
		Formatter fmt = new Formatter();
		return fmt.format("%.5f/%.5f",
			Utils.toDegrees(latitude),
			Utils.toDegrees(longitude)).toString();
	}

	public String toOSMURL(int zoom) {
		return ("http://www.openstreetmap.org/?lat=" +
			new Formatter().format("%.5f", Utils.toDegrees(latitude)) +
			"&lon=" +
			new Formatter().format("%.5f", Utils.toDegrees(longitude)) +
			"&zoom=" +
			zoom);
	}

    	public String toOSMURL() {
		return toOSMURL(17);
	}
}
