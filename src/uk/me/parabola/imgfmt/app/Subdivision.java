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
 * Create date: 07-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.ArrayList;

/**
 * The map is divided into areas, depending on the zoom level.  These are
 * known as subdivisions.
 * <p>A subdivision 'belongs' to a zoom level and cannot be intepreted correctly
 * with out knowing the <i>bitsPerCoord</i> of the associated zoom level.
 * <p>Subdivisions also form a tree as subdivisions are further divided at
 * lower levels.
 *
 * @author Steve Ratcliffe
 */
public class Subdivision {
	static private Logger log = Logger.getLogger(Subdivision.class);

	private int rgnPointer;
	
	private boolean hasPoints;
	private boolean hasIndPoints;
	private boolean hasPolylines;
	private boolean hasPolygons;

	// The location of the central point
	private int longitude;
	private int latitude;

	// The width and the height in map units scaled by the bits-per-coordinate
	// that applies at the map level.
	private int width;
	private int height;

	private int number;

	// Set if this is the last one.
	private boolean last;

	private List<Subdivision> divisions = new ArrayList<Subdivision>();

	public Subdivision(int latitude, int longitude, int width, int height) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.width = width;
		this.height = height;
	}

	/**
	 * Get the bounds of this subdivision.
	 *
	 * @return The area that this subdivision covers.
	 */
	public Area getBounds() {
		Area b = new Area(latitude-height, longitude-width,
				latitude+height, longitude+width);
		return b;
	}

	// XXX temporary hack
	public Area getBounds(int bits) {
		int h = height << (24-bits);
		int w = width << (24 - bits);
		Area b = new Area(latitude-h, longitude-w,
				latitude+h, longitude+w);
		return b;
	}

	/**
	 * Format this record to the file.
	 *
	 * @param file The file to write to.
	 */
	public void write(ImgFile file) {
		file.put3(rgnPointer);
		file.put(getType());
		file.put3(longitude);
		file.put3(latitude);
		log.debug("last is " + last);
		file.putChar((char) (width | ((last)? 0x8000: 0)));
		file.putChar((char) height);

		if (!divisions.isEmpty()) {
			file.putChar((char) getNextLevel());
		}
	}

	/**
	 * Get the number of the first subdivision at the next level.
	 * @return The first subdivision at the next level.
	 */
	public int getNextLevel() {
		return divisions.get(0).getNumber();
	}

	/**
	 * Add this subdivision as our child at the next level.  Each subdivision
	 * can be further divided into smaller divisions.  They form a tree like
	 * arrangement.
	 *
	 * @param sd One of our subdivisions.
	 */
	public void addSubdivision(Subdivision sd) {
		divisions.add(sd);
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int n) {
		number = n;
	}

	public boolean isLast() {
		return last;
	}

	public void setLast(boolean last) {
		this.last = last;
	}

	public void setRgnPointer(int rgnPointer) {
		this.rgnPointer = rgnPointer;
	}

	public int getLongitude() {
		return longitude;
	}

	public int getLatitude() {
		return latitude;
	}

	public void setHasPoints(boolean hasPoints) {
		this.hasPoints = hasPoints;
	}

	public void setHasIndPoints(boolean hasIndPoints) {
		this.hasIndPoints = hasIndPoints;
	}

	public void setHasPolylines(boolean hasPolylines) {
		this.hasPolylines = hasPolylines;
	}

	public void setHasPolygons(boolean hasPolygons) {
		this.hasPolygons = hasPolygons;
	}

	/**
	 * Get a type that shows if this area has lines, points etc.
	 *
	 * @return A code showing what kinds of element are in this subdivision.
	 */
	private byte getType() {
		byte b = 0;
		if (hasPoints)
			b |= 0x10;
		if (hasIndPoints)
			b |= 0x20;
		if (hasPolylines)
			b |= 0x40;
		if (hasPolygons)
			b |= 0x80;

		return b;
	}

	/**
	 * Create a subdivision at a given zoom level.
	 *
	 * @param area The (unshifted) area that the subdivision covers.
	 * @param zoom The zoom level that this division occupies.
	 * @return A new subdivision.
	 */
	public Subdivision createSubdivision(Area area, Zoom zoom) {
		Subdivision div = createDiv(area, zoom);
		addSubdivision(div);
		return div;
	}

	/**
	 * This should be called only once per map to create the top level
	 * subdivision.  The top level subdivision covers the whole map and it
	 * must be empty.
	 *
	 * @param area The area bounded by the map.
	 * @param zoom The zoom level which must be the highest (least detailed)
	 * zoom in the map.
	 * @return The new subdivision.
	 */
	public static Subdivision topLevelSubdivision(Area area, Zoom zoom) {
		return createDiv(area, zoom);
	}

	/**
	 * Does the work of the methods that create subdivisions.
	 * @param area The area.
	 * @param zoom The zoom level.
	 * @return A new subdivision.
	 */
	private static Subdivision createDiv(Area area, Zoom zoom) {
		// Get the central point of the area.
		int lat = (area.getMinLat() + area.getMaxLat())/2;
		int lng = (area.getMinLong() + area.getMaxLong())/2;

		// Get the half width and height of the area and adjust by the
		// bits per coord.
		int width = (area.getMaxLong() - area.getMinLong())/2;
		width >>= 24 - zoom.getBitsPerCoord();
		int height = (area.getMaxLat() - area.getMinLat())/2;
		height >>= 24 - zoom.getBitsPerCoord();

		Subdivision div = new Subdivision(lat, lng, width, height);
		zoom.addSubdivision(div);

		return div;
	}
}
