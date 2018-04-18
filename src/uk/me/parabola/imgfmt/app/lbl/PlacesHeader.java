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

import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

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
	private int POIGlobalFlags ;

	void setPOIGlobalFlags(int flags) {
		this.POIGlobalFlags = flags;
	}

	int getPOIGlobalFlags() {
		return POIGlobalFlags;
	}

	void writeFileHeader(ImgFileWriter writer) {
		writer.put4(country.getPosition());
		writer.put4(country.getSize());
		writer.put2u(country.getItemSize());
		writer.put4(0);

		writer.put4(region.getPosition());
		writer.put4(region.getSize());
		writer.put2u(region.getItemSize());
		writer.put4(0);

		writer.put4(city.getPosition());
		writer.put4(city.getSize());
		writer.put2u(city.getItemSize());
		writer.put4(0);

		writer.put4(poiIndex.getPosition());
		writer.put4(poiIndex.getSize());
		writer.put2u(poiIndex.getItemSize());
		writer.put4(0);

		writer.put4(poiProperties.getPosition());
		writer.put4(poiProperties.getSize());
		writer.put1u(0); // offset multiplier

		// mb 5/9/2009 - discovered that Garmin maps can contain more
		// than 8 bits of POI global flags - have seen the 9th bit set
		// to indicate the presence of some extra POI info (purpose
		// unknown but it starts with a byte that contains the number
		// of further bytes to read << 1) - therefore, this group
		// should probably be: 16 bits of POI global flags followed by
		// 16 zero bits rather than 8 bits of flags and 24 zero bits
		writer.put1u(POIGlobalFlags); // properties global mask
		writer.put2u(0);
		writer.put1u(0);

		writer.put4(poiTypeIndex.getPosition());
		writer.put4(poiTypeIndex.getSize());
		writer.put2u(poiTypeIndex.getItemSize());
		writer.put4(0);

		writer.put4(zip.getPosition());
		writer.put4(zip.getSize());
		writer.put2u(zip.getItemSize());
		writer.put4(0);

		writer.put4(highway.getPosition());
		writer.put4(highway.getSize());
		writer.put2u(highway.getItemSize());
		writer.put4(0);

		writer.put4(exitFacility.getPosition());
		writer.put4(exitFacility.getSize());
		writer.put2u(exitFacility.getItemSize());
		writer.put4(0);

		writer.put4(highwayData.getPosition());
		writer.put4(highwayData.getSize());
		writer.put2u(highwayData.getItemSize());
		writer.put4(0);
	}

	void readFileHeader(ImgFileReader reader) {
		reader.position(0x1f);

		country.readSectionInfo(reader, true);
		reader.get4();

		region.readSectionInfo(reader, true);
		reader.get4();

		city.readSectionInfo(reader, true);
		reader.get4();

		poiIndex.readSectionInfo(reader, true);
		reader.get4();

		poiProperties.readSectionInfo(reader, false);
		reader.get(); // offset multiplier

		POIGlobalFlags = reader.get1u();
		reader.get2u();
		reader.get();

		poiTypeIndex.readSectionInfo(reader, true);
		reader.get4();

		zip.readSectionInfo(reader, true);
		reader.get4();

		highway.readSectionInfo(reader, true);
		reader.get4();

		exitFacility.readSectionInfo(reader, true);
		reader.get4();

		highwayData.readSectionInfo(reader, true);
		reader.get4();
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

	public int getNumZips() {
		return zip.getNumItems();
	}
	
	public int getPoiPropertiesStart() {
		return poiProperties.getPosition();
	}
	public int getPoiPropertiesEnd() {
		return poiProperties.getEndPos();
	}

	public int getCitiesStart() {
		return city.getPosition();
	}
	public int getCitiesEnd() {
		return city.getEndPos();
	}
	
	public int getNumExits() {
		return exitFacility.getNumItems();
	}

	public int getCountriesStart() {
		return country.getPosition();
	}

	public int getCountriesEnd() {
		return country.getEndPos();
	}

	public int getRegionsStart() {
		return region.getPosition();
	}

	public int getRegionsEnd() {
		return region.getEndPos();
	}

	public int getNumHighways() {
		return highway.getNumItems();
	}
	
	public int getZipsStart() {
		return zip.getPosition();
	}
	
	public int getZipsEnd() {
		return zip.getEndPos();
	}	
}
