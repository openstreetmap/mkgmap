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
import java.util.Locale;

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
public class Coord implements Comparable<Coord> {
	private final int latitude;
	private final int longitude;
	private byte highwayCount; // number of highways that use this point
	private boolean onBoundary;	// true if point lies on a boundary
	private boolean preserved; // true if point should not be filtered out

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
		// don't let it wrap
		if(highwayCount < Byte.MAX_VALUE)
			++highwayCount;
	}

	public boolean getOnBoundary() {
		return onBoundary;
	}

	public void setOnBoundary(boolean onBoundary) {
		this.onBoundary = onBoundary;
	}

	public boolean preserved() {
		return preserved;
	}

	public void preserved(boolean preserved) {
		this.preserved = preserved;
	}

	public int hashCode() {
		return latitude+longitude;
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Coord))
			return false;
		Coord other = (Coord) obj;
		return latitude == other.latitude && longitude == other.longitude;
	}

	/**
	 * Distance to other point in meters.
	 */
	public double distance(Coord other) {
		return quickDistance(other);
	}

	protected double slowDistance(Coord other) {
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

	public double quickDistance(Coord other){
		return 40075000 * Math.sqrt(distanceInDegreesSquared(other)) / 360;
	}

	public double distanceInDegreesSquared(Coord other) {
		if (equals(other))
			return 0;

		double lat1 = Utils.toDegrees(getLatitude());
		double lat2 = Utils.toDegrees(other.getLatitude());
		double long1 = Utils.toDegrees(getLongitude());
		double long2 = Utils.toDegrees(other.getLongitude());
				
		double latDiff;
		if (lat1 < lat2)
			latDiff = lat2 - lat1;
		else
			latDiff = lat1 - lat2;	
		if (latDiff > 90)
			latDiff -= 180;

		double longDiff;
		if (long1 < long2)
			longDiff = long2 - long1;
		else
			longDiff = long1 - long2;
		if (longDiff > 180)
			longDiff -= 360;

		// scale longDiff by cosine of average latitude
		longDiff *= Math.cos(Math.PI / 180 * Math.abs((lat1 + lat2) / 2));

		return (latDiff * latDiff) + (longDiff * longDiff);
	}

	public Coord makeBetweenPoint(Coord other, double fraction) {
		return new Coord((int)(latitude + (other.latitude - latitude) * fraction),
						 (int)(longitude + (other.longitude - longitude) * fraction));
	}


	// returns bearing (in degrees) from current point to another point
	public double bearingTo(Coord point) {
		double lat1 = Utils.toRadians(latitude);
		double lat2 = Utils.toRadians(point.latitude);
		double lon1 = Utils.toRadians(longitude);
		double lon2 = Utils.toRadians(point.longitude);

		double dlon = lon2 - lon1;

		double y = Math.sin(dlon) * Math.cos(lat2);
		double x = Math.cos(lat1)*Math.sin(lat2) -
			Math.sin(lat1)*Math.cos(lat2)*Math.cos(dlon);
		return Math.atan2(y, x) * 180 / Math.PI;
	}

	/**
	 * Sort lexicographically by longitude, then latitude.
	 *
	 * This ordering is used for sorting entries in NOD3.
	 */
	public int compareTo(Coord other) {
		if (longitude == other.getLongitude())
			if (latitude == other.getLatitude()) return 0;
			else return latitude > other.getLatitude() ? 1 : -1;
		else
			return longitude > other.getLongitude()? 1: -1;
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

	protected String toOSMURL(int zoom) {
		return ("http://www.openstreetmap.org/?mlat=" +
			new Formatter(Locale.ENGLISH).format("%.5f", Utils.toDegrees(latitude)) +
			"&mlon=" +
			new Formatter(Locale.ENGLISH).format("%.5f", Utils.toDegrees(longitude)) +
			"&zoom=" +
			zoom);
	}

	public String toOSMURL() {
		return toOSMURL(17);
	}
}
