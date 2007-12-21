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
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.log.Logger;

/**
 * @author Steve Ratcliffe
 */
public class TREHeader extends CommonHeader {
	private static final Logger log = Logger.getLogger(TREHeader.class);

	public static final int HEADER_LEN = 120; // Other values are possible

	static final int MAP_LEVEL_REC_SIZE = 4;
	static final char POLYLINE_REC_LEN = 2;
	static final char POLYGON_REC_LEN = 2;
	static final char POINT_REC_LEN = 3;
	static final char COPYRIGHT_REC_SIZE = 0x3;
	static final int SUBDIV_REC_SIZE = 14;
	static final int SUBDIV_REC_SIZE2 = 16;

	// Bounding box.  All units are in map units.
	private Area area = new Area(0,0,0,0);

	private int mapInfoSize;

	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private byte poiDisplayFlags;

	private Section copyright = new Section(COPYRIGHT_REC_SIZE);
	private Section polyline = new Section(POLYLINE_REC_LEN);
	private Section polygon = new Section(POLYLINE_REC_LEN);
	private Section points = new Section(POLYLINE_REC_LEN);

	private int mapId;

	public TREHeader() {
		super(HEADER_LEN, "GARMIN TRE");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ReadStrategy reader) throws ReadFailedException {
		assert reader.position() == COMMON_HEADER_LEN;
		int maxLat = reader.get3();
		int maxLon = reader.get3();
		int minLat = reader.get3();
		int minLon = reader.get3();
		setBounds(new Area(minLat, minLon, maxLat, maxLon));
		log.info("read area is", getBounds());

		// more to do...
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(WriteStrategy writer) {
		writer.put3(area.getMaxLat());
		writer.put3(area.getMaxLong());
		writer.put3(area.getMinLat());
		writer.put3(area.getMinLong());

		writer.putInt(getMapLevelPos());
		writer.putInt(getMapLevelsSize());

		writer.putInt(getSubdivPos());
		writer.putInt(getSubdivSize());

		writer.putInt(copyright.getPosition());
		writer.putInt(copyright.getSize());
		writer.putChar(copyright.getItemSize());

		writer.putInt(0);

		writer.put(getPoiDisplayFlags());

		writer.put3(0x19);
		writer.putInt(0xd0401);

		writer.putChar((char) 1);
		writer.put((byte) 0);

		writer.putInt(polyline.getPosition());
		writer.putInt(polyline.getSize());
		writer.putChar(polyline.getItemSize());

		writer.putChar((char) 0);
		writer.putChar((char) 0);

		writer.putInt(polygon.getPosition());
		writer.putInt(polygon.getSize());
		writer.putChar(polygon.getItemSize());

		writer.putChar((char) 0);
		writer.putChar((char) 0);

		writer.putInt(points.getPosition());
		writer.putInt(points.getSize());
		writer.putChar(points.getItemSize());

		writer.putChar((char) 0);
		writer.putChar((char) 0);

		// Map ID
		writer.putInt(getMapId());

		writer.position(HEADER_LEN);
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
		//this.copyrightSize = copyrightSize;
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
