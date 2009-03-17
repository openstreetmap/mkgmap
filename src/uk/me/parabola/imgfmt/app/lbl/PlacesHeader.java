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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFileReader;

/**
 * This is not a separate header, but rather part of the LBL header.  It is just
 * separated out for convenience.  All the records that have some kind of
 * meaning associated with a place are put here.
 *
 * @author Steve Ratcliffe
 */
public class PlacesHeader {

	private static final char COUNTRY_REC_LEN = 3;
	private static final char REGION_REC_LEN = 5;
	private static final char CITY_REC_LEN = 5;
	private static final char POI_INDEX_REC_LEN = 4;
	private static final char POI_TYPE_INDEX_REC_LEN = 4;
	private static final char ZIP_REC_LEN = 3;
	private static final char HIGHWAY_REC_LEN = 6;
	private static final char EXIT_REC_LEN = 5;
	private static final char HIGHWAYDATA_REC_LEN = 3;

	private final Section country = new Section(COUNTRY_REC_LEN);
	private final Section region = new Section(country, REGION_REC_LEN);
	private final Section city = new Section(region, CITY_REC_LEN);
	private final Section poiIndex = new Section(city, POI_INDEX_REC_LEN);
	private final Section poiProperties = new Section(poiIndex);
	private final Section poiTypeIndex = new Section(poiProperties, POI_TYPE_INDEX_REC_LEN);
	private final Section zip = new Section(poiTypeIndex, ZIP_REC_LEN);
	private final Section highway = new Section(zip, HIGHWAY_REC_LEN);
	private final Section exitFacility = new Section(highway, EXIT_REC_LEN);
	private final Section highwayData = new Section(exitFacility, HIGHWAYDATA_REC_LEN);
	private byte POIGlobalFlags ;

	void setPOIGlobalFlags(byte flags) {
		this.POIGlobalFlags = flags;
	}

	byte getPOIGlobalFlags() {
		return POIGlobalFlags;
	}

	void writeFileHeader(ImgFileWriter writer) {
		writer.putInt(country.getPosition());
		writer.putInt(country.getSize());
		writer.putChar(country.getItemSize());
		writer.putInt(0);

		writer.putInt(region.getPosition());
		writer.putInt(region.getSize());
		writer.putChar(region.getItemSize());
		writer.putInt(0);

		writer.putInt(city.getPosition());
		writer.putInt(city.getSize());
		writer.putChar(city.getItemSize());
		writer.putInt(0);

		writer.putInt(poiIndex.getPosition());
		writer.putInt(poiIndex.getSize());
		writer.putChar(poiIndex.getItemSize());
		writer.putInt(0);

		writer.putInt(poiProperties.getPosition());
		writer.putInt(poiProperties.getSize());
		writer.put((byte) 0); // offset multiplier
		writer.put(POIGlobalFlags); // properties global mask
		writer.putChar((char) 0);
		writer.put((byte) 0);

		writer.putInt(poiTypeIndex.getPosition());
		writer.putInt(poiTypeIndex.getSize());
		writer.putChar(poiTypeIndex.getItemSize());
		writer.putInt(0);

		writer.putInt(zip.getPosition());
		writer.putInt(zip.getSize());
		writer.putChar(zip.getItemSize());
		writer.putInt(0);

		writer.putInt(highway.getPosition());
		writer.putInt(highway.getSize());
		writer.putChar(highway.getItemSize());
		writer.putInt(0);

		writer.putInt(exitFacility.getPosition());
		writer.putInt(exitFacility.getSize());
		writer.putChar(exitFacility.getItemSize());
		writer.putInt(0);

		writer.putInt(highwayData.getPosition());
		writer.putInt(highwayData.getSize());
		writer.putChar(highwayData.getItemSize());
		writer.putInt(0);
	}

	void readFileHeader(ImgFileReader reader) {
		reader.position(0x1f);

		country.setPosition(reader.getInt());
		country.setSize(reader.getInt());
		country.setItemSize(reader.getChar());
		reader.getInt();

		region.setPosition(reader.getInt());
		region.setSize(reader.getInt());
		region.setItemSize(reader.getChar());
		reader.getInt();

		city.setPosition(reader.getInt());
		city.setSize(reader.getInt());
		city.setItemSize(reader.getChar());
		reader.getInt();
	}

	int getLastPos() {
		// Beware this is not really valid until all is written.
		return highwayData.getEndPos();
	}

	void setLabelEnd(int pos) {
		country.setPosition(pos);
	}

	void endCountries(int pos) {
		country.setSize(pos - country.getPosition());
	}

	void endRegions(int pos) {
		region.setSize(pos - region.getPosition());
	}

	void endCity(int pos) {
		city.setSize(pos - city.getPosition());
	}

	void endPOI(int pos) {
		poiProperties.setSize(pos - poiProperties.getPosition());
	}

	void endPOIIndex(int pos) {
		poiIndex.setSize(pos - poiIndex.getPosition());
	}

	void endPOITypeIndex(int pos) {
		poiTypeIndex.setSize(pos - poiTypeIndex.getPosition());
	}

	void endZip(int pos) {
		zip.setSize(pos - zip.getPosition());
	}

	void endHighway(int pos) {
		highway.setSize(pos - highway.getPosition());
	}

	void endExitFacility(int pos) {
		exitFacility.setSize(pos - exitFacility.getPosition());
	}

	void endHighwayData(int pos) {
		highwayData.setSize(pos - highwayData.getPosition());
	}

	public int getNumCities() {
		return city.getNumItems();
	}
}
