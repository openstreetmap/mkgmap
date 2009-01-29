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
package uk.me.parabola.imgfmt.app.trergn;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.LBLFile;
import uk.me.parabola.log.Logger;

/**
 * The map is divided into areas, depending on the zoom level.  These are
 * known as subdivisions.
 *
 * A subdivision 'belongs' to a zoom level and cannot be intepreted correctly
 * without knowing the <i>bitsPerCoord</i> of the associated zoom level.
 *
 * Subdivisions also form a tree as subdivisions are further divided at
 * lower levels.  The subdivisions need to know their child divisions
 * because this information is represented in the map.
 *
 * @author Steve Ratcliffe
 */
public class Subdivision {
	private static final Logger log = Logger.getLogger(Subdivision.class);

	private static final int MAP_POINT = 0;
	private static final int MAP_INDEXED_POINT = 1;
	private static final int MAP_LINE = 2;
	private static final int MAP_SHAPE = 3;

	private final LBLFile lblFile;
	private final RGNFile rgnFile;

	private int rgnPointer;

	private int lastMapElement;

	// The zoom level contains the number of bits per coordinate which is
	// critical for scaling quantities by.
	private final Zoom zoomLevel;

	private boolean hasPoints;
	private boolean hasIndPoints;
	private boolean hasPolylines;
	private boolean hasPolygons;

	private int numPolylines;

	// The location of the central point, not scaled AFAIK
	private final int longitude;
	private final int latitude;

	// The width and the height in map units scaled by the bits-per-coordinate
	// that applies at the map level.
	private final int width;
	private final int height;

	private int number;

	// Set if this is the last one.
	private boolean last;

	private final List<Subdivision> divisions = new ArrayList<Subdivision>();

	/**
	 * Subdivisions can not be created directly, use either the
	 * {@link #topLevelSubdivision} or {@link #createSubdivision} factory
	 * methods.
	 *
	 * @param ifiles The internal files.
	 * @param area The area this subdivision should cover.
	 * @param z The zoom level.
	 */
	private Subdivision(InternalFiles ifiles, Area area, Zoom z) {
		this.lblFile = ifiles.getLblFile();
		this.rgnFile = ifiles.getRgnFile();

		this.zoomLevel = z;

		int shift = getShift();

		this.latitude = (area.getMinLat() + area.getMaxLat())/2;
		this.longitude = (area.getMinLong() + area.getMaxLong())/2;

		int w = (area.getWidth() + (1<<shift)) / 2 >> shift;
		if (w > 0x7fff)
			w = 0x7fff;

		int h = (area.getHeight() + (1 << shift)) / 2 >> shift;
		if (h > 0xffff)
			h = 0xffff;

		this.width = w;
		this.height = h;
	}

	/**
	 * Create a subdivision at a given zoom level.
	 *
	 * @param ifiles The RGN and LBL ifiles.
	 * @param area The (unshifted) area that the subdivision covers.
	 * @param zoom The zoom level that this division occupies.
	 *
	 * @return A new subdivision.
	 */
	public Subdivision createSubdivision(InternalFiles ifiles,
			Area area, Zoom zoom)
	{
		Subdivision div = new Subdivision(ifiles, area, zoom);
		zoom.addSubdivision(div);
		addSubdivision(div);
		return div;
	}

	/**
	 * This should be called only once per map to create the top level
	 * subdivision.  The top level subdivision covers the whole map and it
	 * must be empty.
	 *
	 * @param ifiles The LBL and  RGN ifiles.
	 * @param area The area bounded by the map.
	 * @param zoom The zoom level which must be the highest (least detailed)
     * zoom in the map.
	 * 
	 * @return The new subdivision.
	 */
	public static Subdivision topLevelSubdivision(InternalFiles ifiles,
			Area area, Zoom zoom)
	{
		Subdivision div = new Subdivision(ifiles, area, zoom);
		zoom.addSubdivision(div);
		return div;
	}

	public Zoom getZoom() {
		return zoomLevel;
	}

	/**
	 * Get the shift value, that is the number of bits to left shift by for
	 * values that need to be saved shifted in the file.  Related to the
	 * resolution.
	 *
	 * @return The shift value.  It is 24 minus the number of bits per coord.
	 * @see #getResolution()
	 */
	public final int getShift() {
		return 24 - zoomLevel.getResolution();
	}

	/**
	 * Get the resolution of this division.  Resolution goes from 1 to 24
	 * and the higher the number the more detail there is.
	 *
	 * @return The resolution.
	 */
	public final int getResolution() {
		return zoomLevel.getResolution();
	}

	/**
	 * Format this record to the file.
	 *
	 * @param file The file to write to.
	 */
	public void write(ImgFileWriter file) {
		log.debug("write subdiv", latitude, longitude);
		file.put3(rgnPointer);
		file.put(getType());
		file.put3(longitude);
		file.put3(latitude);
		
		assert width <= 0x7fff;
		assert height <= 0xffff;
		file.putChar((char) (width | ((last) ? 0x8000 : 0)));
		file.putChar((char) height);

		if (!divisions.isEmpty()) {
			file.putChar((char) getNextLevel());
		}
	}

	public Point createPoint(String name) {
		assert hasPoints || hasIndPoints;
		
		Point p = new Point(this);
		Label label = lblFile.newLabel(name);

		p.setLabel(label);
		return p;
	}

	public Polyline createLine(String name) {
		assert hasPolylines;
		Label label = lblFile.newLabel(name);
		Polyline pl = new Polyline(this);

		pl.setLabel(label);
		numPolylines++;
		pl.setNumber(numPolylines);
		return pl;
	}

	public Polygon createPolygon(String name) {
		assert hasPolygons;
		Label label = lblFile.newLabel(name);
		Polygon pg = new Polygon(this);

		pg.setLabel(label);
		return pg;
	}

	public void setNumber(int n) {
		number = n;
	}

	public void setLast(boolean last) {
		this.last = last;
	}

	public void setRgnPointer(int rgnPointer) {
		this.rgnPointer = rgnPointer;
	}

	public int getRgnPointer() {
		return rgnPointer;
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
	 * The following routines answer the question 'does there need to
	 * be a pointer in the rgn section to this area?'.  You need a
	 * pointer for all the regions that exist except the first one.
	 * There is a strict order with points first and finally polygons.
	 *
	 * @return Never needed as if it exists it will be first.
	 */
	public boolean needsPointPtr() {
		return false;
	}

	/**
	 * Needed if it exists and is not first, ie there is a points
	 * section.
	 * @return true if pointer needed
	 */
	public boolean needsIndPointPtr() {
		return hasIndPoints && hasPoints;
	}

	/**
	 * Needed if it exists and is not first, ie there is a points or
	 * indexed points section.
	 * @return true if pointer needed.
	 */
	public boolean needsPolylinePtr() {
		return hasPolylines && (hasPoints || hasIndPoints);
	}

	/**
	 * As this is last in the list it is needed if it exists and there
	 * is another section.
	 * @return true if pointer needed.
	 */
	public boolean needsPolygonPtr() {
		return hasPolygons && (hasPoints || hasIndPoints || hasPolylines);
	}

	public String toString() {
		return "Sub" + zoomLevel + '(' + latitude + ',' + longitude + ')';
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
	 * Get the number of the first subdivision at the next level.
	 * @return The first subdivision at the next level.
	 */
	private int getNextLevel() {
		return divisions.get(0).getNumber();
	}

	public boolean hasNextLevel() {
		return !divisions.isEmpty();
	}

	public void startDivision() {
		rgnFile.startDivision(this);
	}

	/**
	 * Add this subdivision as our child at the next level.  Each subdivision
	 * can be further divided into smaller divisions.  They form a tree like
	 * arrangement.
	 *
	 * @param sd One of our subdivisions.
	 */
	private void addSubdivision(Subdivision sd) {
		divisions.add(sd);
	}

	public int getNumber() {
		return number;
	}

	/**
	 * We are starting to draw the points.  These must be done first.
	 */
	public void startPoints() {
		if (lastMapElement > MAP_POINT)
			throw new IllegalStateException("Points must be drawn first");

		lastMapElement = MAP_POINT;
	}

	/**
	 * We are starting to draw the lines.  These must be done before
	 * polygons.
	 */
	public void startIndPoints() {
		if (lastMapElement > MAP_INDEXED_POINT)
			throw new IllegalStateException("Indexed points must be done before lines and polygons");

		lastMapElement = MAP_INDEXED_POINT;

		rgnFile.setIndPointPtr();
	}

	/**
	 * We are starting to draw the lines.  These must be done before
	 * polygons.
	 */
	public void startLines() {
		if (lastMapElement > MAP_LINE)
			throw new IllegalStateException("Lines must be done before polygons");

		lastMapElement = MAP_LINE;

		rgnFile.setPolylinePtr();
	}

	/**
	 * We are starting to draw the shapes.  This is done last.
	 */
	public void startShapes() {

		lastMapElement = MAP_SHAPE;

		rgnFile.setPolygonPtr();
	}
}
