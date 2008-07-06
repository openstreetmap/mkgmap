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

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

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
	public static final int TRE_188 = 188;

	// The header length to use when creating a file.
	public static final int DEFAULT_HEADER_LEN = TRE_188;

	static final int MAP_LEVEL_REC_SIZE = 4;
	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;
	private static final char COPYRIGHT_REC_SIZE = 0x3;
	static final int SUBDIV_REC_SIZE = 14;
	static final int SUBDIV_REC_SIZE2 = 16;

	// Bounding box.  All units are in map units.
	private Area area = new Area(0,0,0,0);

	private int mapInfoSize;

	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private byte poiDisplayFlags = 0x1;

	private final Section copyright = new Section(COPYRIGHT_REC_SIZE);
	private final Section polyline = new Section(POLYLINE_REC_LEN);
	private final Section polygon = new Section(POLYGON_REC_LEN);
	private final Section points = new Section(POINT_REC_LEN);
	private Section tre7 = new Section(points, (char) 13);
	private Section tre8 = new Section(tre7, (char) 4);
	//private Section tre9 = new Section(tre8);

	private int mapId;

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
		int maxLat = reader.get3();
		int maxLon = reader.get3();
		int minLat = reader.get3();
		int minLon = reader.get3();
		setBounds(new Area(minLat, minLon, maxLat, maxLon));
		log.info("read area is", getBounds());

		// more to do...
		mapLevelPos = reader.getInt();
		mapLevelsSize = reader.getInt();
		subdivPos = reader.getInt();
		subdivSize = reader.getInt();

		copyright.setPosition(reader.getInt());
		copyright.setSize(reader.getInt());
		copyright.setItemSize(reader.getChar());

		int mapInfoOff = mapLevelPos;
		if (subdivPos < mapInfoOff)
			mapInfoOff = subdivPos;
		if (copyright.getPosition() < mapInfoOff)
			mapInfoOff = copyright.getPosition();

		mapInfoSize = mapInfoOff - getHeaderLength();
		
		reader.getInt();
		reader.getInt();
		reader.getInt();

		readSectionInfo(reader, copyright);
		reader.getInt();
	}

	private void readSectionInfo(ImgFileReader reader, Section sect) {
		sect.setPosition(reader.getInt());
		sect.setSize(reader.getInt());
		sect.setItemSize(reader.getChar());
	}

	protected void writeSectionInfo(ImgFileWriter writer, Section section) {
		writer.putInt(section.getPosition());
		writer.putInt(section.getSize());
		if (section.getItemSize() > 0)
			writer.putChar(section.getItemSize());
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.put3(area.getMaxLat());
		writer.put3(area.getMaxLong());
		writer.put3(area.getMinLat());
		writer.put3(area.getMinLong());

		writer.putInt(getMapLevelPos());
		writer.putInt(getMapLevelsSize());

		writer.putInt(getSubdivPos());
		writer.putInt(getSubdivSize());

		writeSectionInfo(writer, copyright);
		writer.putInt(0);

		writer.put(getPoiDisplayFlags());

		writer.put3(0x19);
		writer.putInt(0xd0401);

		writer.putChar((char) 1);
		writer.put((byte) 0);

		writeSectionInfo(writer, polyline);
		writer.putInt(0);
		writeSectionInfo(writer, polygon);
		writer.putInt(0);
		writeSectionInfo(writer, points);
		writer.putInt(0);

		// There are a number of versions of the header with increasing lengths
		if (getHeaderLength() > 116)
			writer.putInt(getMapId());

		if (getHeaderLength() > 120) {
			writer.putInt(0);

			writeSectionInfo(writer, tre7);
			writer.putInt(0); // not usually zero

			writeSectionInfo(writer, tre8);
			writer.putChar((char) 0);
			writer.putInt(0);
		}

		if (getHeaderLength() > 154) {
			MapValues mv = new MapValues(mapId, getHeaderLength());
			mv.calculate();
			writer.putInt(mv.value(0));
			writer.putInt(mv.value(1));
			writer.putInt(mv.value(2));
			writer.putInt(mv.value(3));

			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putChar((char) 0);
			writer.putInt(0);
		}
		
		writer.position(getHeaderLength());
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

	public void setPoiDisplayFlags(byte poiDisplayFlags) {
		this.poiDisplayFlags = poiDisplayFlags;
	}

	public int getMapInfoSize() {
		return mapInfoSize;
	}

	public void setMapInfoSize(int mapInfoSize) {
		this.mapInfoSize = mapInfoSize;
	}

	protected int getMapLevelPos() {
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

	protected int getSubdivPos() {
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

	protected int getCopyrightPos() {
		return copyright.getPosition();
	}

	public void setCopyrightPos(int copyrightPos) {
		//this.copyrightPos = copyrightPos;
		copyright.setPosition(copyrightPos);
	}

	public void incCopyrightSize() {
		copyright.inc();
	}

	public Section getCopyrightSection() {
		return copyright;
	}

	protected byte getPoiDisplayFlags() {
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

	protected int getMapId() {
		return mapId;
	}

}
