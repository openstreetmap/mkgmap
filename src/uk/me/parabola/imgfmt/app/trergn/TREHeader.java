/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * @author Steve Ratcliffe
 */
public class TREHeader extends CommonHeader {
	private static final Logger log = Logger.getLogger(TREHeader.class);

	// The tre section comes in different versions with different length
	// headers.  We just refer to them by the header length for lack of any
	// better description.
	public static final int TRE_120 = 120;
	public static final int TRE_184 = 184;
	private static final int TRE_188 = 188;

	// The header length to use when creating a file.
	private static final int DEFAULT_HEADER_LEN = TRE_188;

	// A map has a display priority that determines which map is on top
	// when two maps cover the same area.
	private static final int DEFAULT_DISPLAY_PRIORITY = 0x19;

	static final int MAP_LEVEL_REC_SIZE = 4;
	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;
	private static final char COPYRIGHT_REC_SIZE = 0x3;
	private static final char EXT_TYPE_OFFSETS_REC_LEN = 13;
	private static final char EXT_TYPE_OVERVIEWS_REC_LEN = 4;
	static final int SUBDIV_REC_SIZE = 14;
	static final int SUBDIV_REC_SIZE2 = 16;
	
	public static final int POI_FLAG_DETAIL = 0x1; 
	public static final int POI_FLAG_TRANSPARENT = 0x2;
	public static final int POI_FLAG_STREET_BEFORE_HOUSENUMBER = 0x4;
	public static final int POI_FLAG_POSTALCODE_BEFORE_CITY = 0x8;
	public static final int POI_FLAG_DRIVE_ON_LEFT = 0x20;

	// Bounding box.  All units are in map units.
	private Area area = new Area(0,0,0,0);

	private int mapInfoSize;

	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private int poiDisplayFlags;

	private int displayPriority = DEFAULT_DISPLAY_PRIORITY;

	private final Section copyright = new Section(COPYRIGHT_REC_SIZE);
	private final Section polyline = new Section(POLYLINE_REC_LEN);
	private final Section polygon = new Section(POLYGON_REC_LEN);
	private final Section points = new Section(POINT_REC_LEN);
	private final Section extTypeOffsets = new Section(points, EXT_TYPE_OFFSETS_REC_LEN);
	private final Section extTypeOverviews = new Section(extTypeOffsets, EXT_TYPE_OVERVIEWS_REC_LEN);

	private int numExtTypeAreaTypes;
	private int numExtTypeLineTypes;
	private int numExtTypePointTypes;

	private int mapId;

	private boolean custom;

	public TREHeader() {
		super(DEFAULT_HEADER_LEN, "GARMIN TRE");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		assert reader.position() == COMMON_HEADER_LEN;
		int maxLat = reader.get3s();
		int maxLon = reader.get3s();
		int minLat = reader.get3s();
		int minLon = reader.get3s();
		// fix problem with value 0x800000 that is interpreted as a negative value
		if (maxLon <  minLon && maxLon == -8388608 )
			maxLon = 8388608; // its 180 degrees, not -180
		
		setBounds(new Area(minLat, minLon, maxLat, maxLon));
		log.info("read area is", getBounds());

		// more to do...
		mapLevelPos = reader.get4();
		mapLevelsSize = reader.get4();
		subdivPos = reader.get4();
		subdivSize = reader.get4();

		copyright.readSectionInfo(reader, true);
		reader.get4();

		poiDisplayFlags = reader.get1u();
		displayPriority = reader.get3u();
		reader.get4();
		reader.get2u();
		reader.get();

		polyline.readSectionInfo(reader, true);
		reader.get4();
		polygon.readSectionInfo(reader, true);
		reader.get4();
		points.readSectionInfo(reader, true);
		reader.get4();

		int mapInfoOff = mapLevelPos;
		if (subdivPos < mapInfoOff)
			mapInfoOff = subdivPos;
		if (copyright.getPosition() < mapInfoOff)
			mapInfoOff = copyright.getPosition();

		mapInfoSize = mapInfoOff - getHeaderLength();
		if (getHeaderLength() > 116) {
			reader.position(116);
			mapId = reader.get4();
		}
		if (getHeaderLength() > 120) {
			reader.get4();
			extTypeOffsets.readSectionInfo(reader, true);
		}
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.put3s(area.getMaxLat());
		// handle special case, write -180 instead of 180 to avoid assertion
		if (area.getMaxLong() == Utils.MAX_LON_MAP_UNITS)
			writer.put3s(Utils.MIN_LON_MAP_UNITS);
		else
			writer.put3s(area.getMaxLong());
		writer.put3s(area.getMinLat());
		writer.put3s(area.getMinLong());

		writer.put4(getMapLevelsPos());
		writer.put4(getMapLevelsSize());

		writer.put4(getSubdivPos());
		writer.put4(getSubdivSize());

		copyright.writeSectionInfo(writer);
		writer.put4(0);

		writer.put1u(getPoiDisplayFlags());

		writer.put3u(displayPriority);
		if (custom)
			writer.put4(0x170401);
		else
			writer.put4(0x110301);
		
		writer.put2u(1);
		writer.put1u(0);

		polyline.writeSectionInfo(writer);
		writer.put4(0);
		polygon.writeSectionInfo(writer);
		writer.put4(0);
		points.writeSectionInfo(writer);
		writer.put4(0);

		// There are a number of versions of the header with increasing lengths
		if (getHeaderLength() > 116)
			writer.put4(getMapId());

		if (getHeaderLength() > 120) {
			writer.put4(0);

			// The record size must be zero if the section is empty for compatibility
			// with cpreview.
			if (extTypeOffsets.getSize() == 0)
				extTypeOffsets.setItemSize(0);
			extTypeOffsets.writeSectionInfo(writer, true);

			// the second byte value of 6 appears to mean "extended
			// type info present" - a value of 4 has been seen in some
			// maps but those maps contain something else in these two
			// sections and not extended type info - the 7 in the
			// bottom byte could possibly be a bitmask to say which
			// types are present (line, area, point) but this is just
			// conjecture
			writer.put4(0x0607);

			extTypeOverviews.writeSectionInfo(writer);
			writer.put2u(numExtTypeLineTypes);
			writer.put2u(numExtTypeAreaTypes);
			writer.put2u(numExtTypePointTypes);
		}

		if (getHeaderLength() > 154) {
			MapValues mv = new MapValues(mapId, getHeaderLength());
			mv.calculate();
			writer.put4(mv.value(0));
			writer.put4(mv.value(1));
			writer.put4(mv.value(2));
			writer.put4(mv.value(3));

			writer.put4(0);
			writer.put4(0);
			writer.put4(0);
			writer.put2u(0);
			writer.put4(0);
		}
		
		writer.position(getHeaderLength());
	}

	public void config(EnhancedProperties props) {
		String key = "draw-priority";
		if (props.containsKey(key))
			setDisplayPriority(props.getProperty(key, 0x19));

		if (props.containsKey("transparent"))
			poiDisplayFlags |= POI_FLAG_TRANSPARENT;
		custom = props.containsKey("custom");
			
	}
	
	/**
	 * Set the bounds based upon the latitude and longitude in degrees.
	 * @param area The area bounded by the map.
	 */
	public void setBounds(Area area) {
		this.area = area;
	}

	public Area getBounds() {
		return area;
	}

	public void setMapId(int id) {
		mapId = id;
	}

	public void setDriveOnLeft(boolean dol) {
		if (dol)
			this.poiDisplayFlags |= POI_FLAG_DRIVE_ON_LEFT; 
	}

	public void addPoiDisplayFlags(int poiDisplayFlags) {
		this.poiDisplayFlags |= poiDisplayFlags;
	}	

	public int getMapInfoSize() {
		return mapInfoSize;
	}

	public void setMapInfoSize(int mapInfoSize) {
		this.mapInfoSize = mapInfoSize;
	}

	public int getMapLevelsPos() {
		return mapLevelPos;
	}

	public void setMapLevelPos(int mapLevelPos) {
		this.mapLevelPos = mapLevelPos;
	}

	public int getMapLevelsSize() {
		return mapLevelsSize;
	}

	public void setMapLevelsSize(int mapLevelsSize) {
		this.mapLevelsSize = mapLevelsSize;
	}

	public int getSubdivPos() {
		return subdivPos;
	}

	public void setSubdivPos(int subdivPos) {
		this.subdivPos = subdivPos;
	}

	public int getSubdivSize() {
		return subdivSize;
	}

	public void setSubdivSize(int subdivSize) {
		this.subdivSize = subdivSize;
	}

	public void setCopyrightPos(int copyrightPos) {
		//this.copyrightPos = copyrightPos;
		copyright.setPosition(copyrightPos);
	}

	public void incCopyrightSize() {
		copyright.inc();
	}

	protected int getPoiDisplayFlags() {
		return poiDisplayFlags;
	}

	public void setPolylinePos(int polylinePos) {
		polyline.setPosition(polylinePos);
	}

	public void incPolylineSize() {
		polyline.inc();
	}

	public void setPolygonPos(int polygonPos) {
		polygon.setPosition(polygonPos);
	}

	public void incPolygonSize() {
		polygon.inc();
	}

	public void setPointPos(int pointPos) {
		points.setPosition(pointPos);
	}

	public void incPointSize() {
		points.inc();
	}

	public void setExtTypeOffsetsPos(int pos) {
		extTypeOffsets.setPosition(pos);
	}

	public void incExtTypeOffsetsSize() {
		extTypeOffsets.inc();
	}

	public void setExtTypeOverviewsPos(int pos) {
		extTypeOverviews.setPosition(pos);
	}

	public void incExtTypeOverviewsSize() {
		extTypeOverviews.inc();
	}

	public void incNumExtTypeAreaTypes() {
		++numExtTypeAreaTypes;
	}

	public void incNumExtTypeLineTypes() {
		++numExtTypeLineTypes;
	}
	public void incNumExtTypePointTypes() {
		++numExtTypePointTypes;
	}

	public int getMapId() {
		return mapId;
	}

	protected void setDisplayPriority(int displayPriority) {
		this.displayPriority = displayPriority;
	}

	public int getDisplayPriority() {
		return displayPriority;
	}

	public int getExtTypeOffsetsPos() {
		return extTypeOffsets.getPosition();
	}
	public int getExtTypeOffsetsSize() {
		return extTypeOffsets.getSize();
	}
	public int getExtTypeSectionSize() {
		return extTypeOffsets.getItemSize();
	}

	public Section getCopyrightSection() {
		return copyright;
	}
}
