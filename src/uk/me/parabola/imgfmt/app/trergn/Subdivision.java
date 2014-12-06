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
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.LBLFile;
import uk.me.parabola.log.Logger;

/**
 * The map is divided into areas, depending on the zoom level.  These are
 * known as subdivisions.
 *
 * A subdivision 'belongs' to a zoom level and cannot be interpreted correctly
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

	// The start pointer is set for read and write.  The end pointer is only
	// set for subdivisions that are read from a file.
	private int startRgnPointer;
	private int endRgnPointer;

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

	private final List<Subdivision> divisions = new ArrayList<>();

	private int extTypeAreasOffset;
	private int extTypeLinesOffset;
	private int extTypePointsOffset;
	private int extTypeAreasSize;
	private int extTypeLinesSize;
	private int extTypePointsSize;

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
		int mask = getMask();

		// Calculate the center, move it right and up so that it lies on a point
		// which is divisible by 2 ^shift
		this.latitude = Utils.roundUp((area.getMinLat() + area.getMaxLat())/2, shift);
		this.longitude = Utils.roundUp((area.getMinLong() + area.getMaxLong())/2, shift);
		int w = 2 * (longitude - area.getMinLong());
		int h = 2 * (latitude - area.getMinLat());
		
		// encode the values for the img format
		w = ((w + 1)/2 + mask) >> shift;
		h = ((h + 1)/2 + mask) >> shift;
		
		if (w > 0x7fff) {
			log.warn("Subdivision width is " + w + " at " + getCenter());
			w = 0x7fff;
		}

		if (h > 0xffff) {
			log.warn("Subdivision height is " + h + " at " + getCenter());
			h = 0xffff;
		}

		this.width = w;
		this.height = h;
	}

	private Subdivision(Zoom z, SubdivData data) {
		lblFile = null;
		rgnFile = null;
		zoomLevel = z;
		latitude = data.getLat();
		longitude = data.getLon();
		this.width = data.getWidth();
		this.height = data.getHeight();

		startRgnPointer = data.getRgnPointer();
		endRgnPointer = data.getEndRgnOffset();

		int elem = data.getFlags();
		if ((elem & 0x10) != 0)
			setHasPoints(true);
		if ((elem & 0x20) != 0)
			setHasIndPoints(true);
		if ((elem & 0x40) != 0)
			setHasPolylines(true);
		if ((elem & 0x80) != 0)
			setHasPolygons(true);
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

	/**
	 * Create a subdivision that only contains the number.  This is only
	 * used when reading cities and similar such usages that do not really
	 * require the full subdivision to be present.
	 * @param number The subdivision number.
	 * @return An empty subdivision.  Any operation other than getting the
	 * subdiv number is likely to fail.
	 */
	public static Subdivision createEmptySubdivision(int number) {
		Subdivision sd = new Subdivision(null, new SubdivData(0,0,0,0,0,0,0));
		sd.setNumber(number);
		return sd;
	}

	public static Subdivision readSubdivision(Zoom zoom, SubdivData subdivData) {
		return new Subdivision(zoom, subdivData);
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
	 * Get the shift mask.  The bits that will be lost due to the resolution
	 * shift level.
	 *
	 * @return A bit mask with the lower <i>shift</i> bits set.
	 */
	protected int getMask() {
		return (1 << getShift()) - 1;
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
		file.put3(startRgnPointer);
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
		Point p = new Point(this);
		Label label = lblFile.newLabel(name);

		p.setLabel(label);
		return p;
	}

	public Polyline createLine(String[] labels) {
		// don't be tempted to "trim()" the name as it zaps the highway shields
		Label label = lblFile.newLabel(labels[0]);
		String nameSansGC = Label.stripGarminCodes(labels[0]);
		Polyline pl = new Polyline(this);

		pl.setLabel(label);

		if(labels[1] != null) {
			// ref may contain multiple ids separated by ";"
			int maxSetIdx = 3;
			if (labels[3] == null) {
				if (labels[2] == null) {
					maxSetIdx = 1;
				} else {
					maxSetIdx = 2;
				}
			}

			String[] refs = Arrays.copyOfRange(labels, 1, maxSetIdx+1);
			if(refs.length == 1) {
				// don't bother to add a single ref that looks the
				// same as the name (sans shield) because it doesn't
				// change the routing directions
				String tr = refs[0].trim();
				String trSansGC = Label.stripGarminCodes(tr);
				if(trSansGC.length() > 0 &&
						!trSansGC.equalsIgnoreCase(nameSansGC)) {
					pl.addRefLabel(lblFile.newLabel(tr));
				}
			}
			else if (refs.length > 1){
				// multiple refs, always add the first so that it will
				// be used in routing instructions when the name has a
				// shield prefix
				pl.addRefLabel(lblFile.newLabel(refs[0].trim()));

				// only add the remaining refs if they differ from the
				// name (sans shield)
				for(int i = 1; i < refs.length; ++i) {
					String tr = refs[i].trim();
					String trSansGC = Label.stripGarminCodes(tr);
					if(trSansGC.length() > 0 &&
							!trSansGC.equalsIgnoreCase(nameSansGC)) {
						pl.addRefLabel(lblFile.newLabel(tr));
					}
				}
			}
		}
		return pl;
	}

	public void setPolylineNumber(Polyline pl) {
		pl.setNumber(++numPolylines);
	}

	public Polygon createPolygon(String name) {
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

	public void setStartRgnPointer(int startRgnPointer) {
		this.startRgnPointer = startRgnPointer;
	}

	public int getStartRgnPointer() {
		return startRgnPointer;
	}

	public int getEndRgnPointer() {
		return endRgnPointer;
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

	public boolean hasPoints() {
		return hasPoints;
	}

	public boolean hasIndPoints() {
		return hasIndPoints;
	}

	public boolean hasPolylines() {
		return hasPolylines;
	}

	public boolean hasPolygons() {
		return hasPolygons;
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
		return "Sub" + zoomLevel + '(' + getCenter().toOSMURL() + ')';
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

	public int getExtTypeAreasOffset() {
		return extTypeAreasOffset;
	}

	public int getExtTypeLinesOffset() {
		return extTypeLinesOffset;
	}

	public int getExtTypePointsOffset() {
		return extTypePointsOffset;
	}

	public int getExtTypeAreasSize() {
		return extTypeAreasSize;
	}

	public int getExtTypeLinesSize() {
		return extTypeLinesSize;
	}

	public int getExtTypePointsSize() {
		return extTypePointsSize;
	}

	public void startDivision() {
		rgnFile.startDivision(this);
		extTypeAreasOffset = rgnFile.getExtTypeAreasSize();
		extTypeLinesOffset = rgnFile.getExtTypeLinesSize();
		extTypePointsOffset = rgnFile.getExtTypePointsSize();
	}

	public void endDivision() {
		extTypeAreasSize = rgnFile.getExtTypeAreasSize() - extTypeAreasOffset;
		extTypeLinesSize = rgnFile.getExtTypeLinesSize() - extTypeLinesOffset;
		extTypePointsSize = rgnFile.getExtTypePointsSize() - extTypePointsOffset;
	}

	public void writeExtTypeOffsetsRecord(ImgFileWriter file) {
		file.putInt(extTypeAreasOffset);
		file.putInt(extTypeLinesOffset);
		file.putInt(extTypePointsOffset);
		int kinds = 0;
		if(extTypeAreasSize != 0)
			++kinds;
		if(extTypeLinesSize != 0)
			++kinds;
		if(extTypePointsSize != 0)
			++kinds;
		file.put((byte)kinds);
	}

	public void writeLastExtTypeOffsetsRecord(ImgFileWriter file) {
		file.putInt(rgnFile.getExtTypeAreasSize());
		file.putInt(rgnFile.getExtTypeLinesSize());
		file.putInt(rgnFile.getExtTypePointsSize());
		file.put((byte)0);
	}

	/**
	 * Read offsets for extended type data and set sizes for predecessor sub-div.
	 * Corresponds to {@link #writeExtTypeOffsetsRecord(ImgFileWriter)} 
	 * @param reader the reader
	 * @param sdPrev the pred. sub-div or null
	 */
	public void readExtTypeOffsetsRecord(ImgFileReader reader,
			Subdivision sdPrev) {
		extTypeAreasOffset = reader.getInt();
		extTypeLinesOffset = reader.getInt();
		extTypePointsOffset = reader.getInt();
		reader.get();
		if (sdPrev != null){
			sdPrev.extTypeAreasSize = extTypeAreasOffset - sdPrev.extTypeAreasOffset;
			sdPrev.extTypeLinesSize = extTypeLinesOffset - sdPrev.extTypeLinesOffset;
			sdPrev.extTypePointsSize = extTypePointsOffset - sdPrev.extTypePointsOffset;
		}
	}
	/**
	 * Set the sizes for the extended type data. See {@link #writeLastExtTypeOffsetsRecord(ImgFileWriter)} 
	 */
	public void readLastExtTypeOffsetsRecord(ImgFileReader reader) {
		extTypeAreasSize = reader.getInt() - extTypeAreasOffset;
		extTypeLinesSize = reader.getInt() - extTypeLinesOffset;
		extTypePointsSize = reader.getInt() - extTypePointsOffset;
		byte test = reader.get();
		assert test == 0;
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

	/**
	 * Convert an absolute Lat to a local, shifted value
	 */
	public int roundLatToLocalShifted(int absval) {
		int shift = getShift();
		int val = absval - getLatitude();
		val += ((1 << shift) / 2);
		return (val >> shift);
	}

	/**
	 * Convert an absolute Lon to a local, shifted value
	 */
	public int roundLonToLocalShifted(int absval) {
		int shift = getShift();
		int val = absval - getLongitude();
		val += ((1 << shift) / 2);
		return (val >> shift);
	}


	public Coord getCenter(){
		return new Coord(getLatitude(),getLongitude());
	}

	/**
	 * Get the unshifted width of the subdivision.
	 * @return The true (unshifted) width.
	 */
	public int getWidth() {
		return width << getShift();
	}

	/**
	 * Get the unshifted height of the subdivision.
	 * @return The true (unshifted) height.
	 */
	public int getHeight() {
		return height << getShift();
	}
}
