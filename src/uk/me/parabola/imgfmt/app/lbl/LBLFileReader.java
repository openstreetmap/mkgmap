/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.lbl;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.app.labelenc.EncodedText;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The file that holds all the labels for the map.
 *
 * Would be quite simple, but there are a number of sections that hold country,
 * region, city, etc. records.
 *
 * The main focus of mkgmap is creating files, there are plenty of applications
 * that read and display the data, reading is implemented only to the
 * extent required to support creating the various auxiliary files etc.
 *
 * @author Steve Ratcliffe
 */
public class LBLFileReader extends ImgFile {
	private static final Label NULL_LABEL = new Label("");

	private CharacterDecoder textDecoder = CodeFunctions.getDefaultDecoder();

	private final LBLHeader header = new LBLHeader();

	private final Map<Integer, Label> labels = new HashMap<Integer, Label>();
	private final Map<Integer, POIRecord> pois = new HashMap<Integer, POIRecord>();
	private final Map<Integer, Country> countries = new HashMap<Integer, Country>();
	private final Map<Integer, Region> regions = new HashMap<Integer, Region>();
	private final List<City> cities = new ArrayList<City>();


	public LBLFileReader(ImgChannel chan) {
		setHeader(header);

		setReader(new BufferedImgFileReader(chan));
		header.readHeader(getReader());
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL(
				header.getEncodingType());
		textDecoder = funcs.getDecoder();

		 //TODO read the places file
		//places.init(this, header.getPlaceHeader());
		readLables();

		readCountries();
		readRegions();

		readCities();
		readPoiInfo();
	}

	/**
	 * Get a label by its offset in the label area.
	 * @param offset The offset in the label section.  The offset 0 always
	 * is an empty string.
	 * @return The label including its text.
	 */
	public Label fetchLabel(int offset) {
		Label label = labels.get(offset);
		if (label == null) {
			// TODO this is a problem with the 6 byte decoder in that the actual offset could be one behind
			label = labels.get(offset-1);
		}
		if (label == null) {
			if (offset != 0)
				System.out.println("Invalid offset for label " + offset);
			return NULL_LABEL;
		}
		else
			return label;
	}

	/**
	 * Get a list of cites.  This is not cached here.
	 * @return A list of City objects.
	 */
	public List<City> getCities() {
		return cities;
	}

	public List<Country> getCountries() {
		return new ArrayList<Country>(countries.values());
	}

	public List<Region> getRegions() {
		return new ArrayList<Region>(regions.values());
	}

	/**
	 * Return POI information.
	 * @param offset The offset of the poi information in the header.
	 * @return Returns a poi record at the given offset.  Returns null if
	 * there isn't one at that offset (probably a bug if that does happen though...).
	 */
	public POIRecord fetchPoi(int offset) {
		return pois.get(offset);
	}

	/**
	 * Read a cache the countries. These are used when reading cities.
	 */
	private void readCountries() {
		ImgFileReader reader = getReader();

		PlacesHeader placeHeader = header.getPlaceHeader();

		int start = placeHeader.getCountriesStart();
		int end = placeHeader.getCountriesEnd();

		reader.position(start);
		int index = 1;
		while (reader.position() < end) {
			int offset = reader.get3();
			Label label = fetchLabel(offset);

			if (label != null) {
				Country country = new Country(index);
				country.setLabel(label);
				countries.put(index, country);
			}
			index++;
		}
	}

	/**
	 * Read an cache the regions.  These are used when reading cities.
	 */
	private void readRegions() {
		ImgFileReader reader = getReader();

		PlacesHeader placeHeader = header.getPlaceHeader();

		int start = placeHeader.getRegionsStart();
		int end = placeHeader.getRegionsEnd();

		reader.position(start);
		int index = 1;
		while (reader.position() < end) {
			int country = reader.getChar();
			int offset = reader.get3();
			Label label = fetchLabel(offset);
			if (label != null) {
				Region region = new Region(countries.get(country));
				region.setIndex(index);
				region.setLabel(label);

				regions.put(index, region);
			}

			index++;
		}
	}

	/**
	 * Read in the city section and cache the results here.  They are needed
	 * to read in the POI properties section.
	 */
	private void readCities() {
		PlacesHeader placeHeader = header.getPlaceHeader();
		int start = placeHeader.getCitiesStart();
		int end = placeHeader.getCitiesEnd();

		ImgFileReader reader = getReader();

		// Since cities are indexed starting from 1, we add a null one at index 0

		reader.position(start);
		int index = 1;
		while (reader.position() < end) {
			// First is either a label offset or a point/subdiv combo, we
			// don't know until we have read further
			int label = reader.get3();

			int info = reader.getChar();


			City city;
			if ((info & 0x4000) == 0) {
				Region region = regions.get(info & 0x3fff);
				city = new City(region);
			} else {
				Country country = countries.get(info & 0x3fff);
				city = new City(country);
			}

			city.setIndex(index);
			if ((info & 0x8000) == 0) {
				city.setSubdivision(Subdivision.createEmptySubdivision(1));
			} else {
				// Has subdiv/point index
				int pointIndex = label & 0xff;
				int subdiv = (label >> 8) & 0xffff;
				city.setPointIndex((byte) pointIndex);
				city.setSubdivision(Subdivision.createEmptySubdivision(subdiv));
			}
			cities.add(city);

			index++;
		}
	}

	/**
	 * Read and cache all the labels.
	 *
	 * Note: It is pretty pointless saving the whole label rather than just
	 * the text, except that other objects take a Lable.  Perhaps this can
	 * be changed.
	 */
	private void readLables() {
		ImgFileReader reader = getReader();

		labels.put(0, NULL_LABEL);

		int start = header.getLabelStart();
		int size =  header.getLabelSize();

		reader.position(start + 1);
		int offset = 1;
		for (int i = 0; i < size; i++) {
			byte b = reader.get();
			if (textDecoder.addByte(b)) {
				String text = recoverText();

				Label l = new Label(text);
				l.setOffset(offset);
				labels.put(offset, l);

				offset = i+1;
			}
		}
	}

	/**
	 * Read all the POI information.
	 * This will create a POIRecord, but we just get the name at the minute.
	 *
	 * TODO: not finished
	 */
	private void readPoiInfo() {
		ImgFileReader reader = getReader();

		PlacesHeader placeHeader = header.getPlaceHeader();
		int poiGlobalFlags = placeHeader.getPOIGlobalFlags();

		int start = placeHeader.getPoiPropertiesStart();
		int end = placeHeader.getPoiPropertiesEnd();

		reader.position(start);

		PoiMasks localMask = makeLocalMask(placeHeader);

		while (reader.position() < end) {
			int poiOffset = position() - start;
			int val = reader.get3();
			int labelOffset = val & 0x3fffff;


			boolean override = (val & 0x800000) != 0;

			POIRecord poi = new POIRecord();
			poi.setLabel(fetchLabel(labelOffset));

			// We have what we want, but now have to find the start of the
			// next record as they are not fixed length.
			int flags;
			boolean hasStreet;
			boolean hasStreetNum;
			boolean hasCity;
			boolean hasZip;
			boolean hasPhone;
			boolean hasHighwayExit;
			boolean hasTides = false;
			boolean hasUnkn = false;

			if (override) {
				flags = reader.get();

				hasStreetNum = (flags & localMask.streetNumMask) != 0;
				hasStreet = (flags & localMask.streetMask) != 0;
				hasCity = (flags & localMask.cityMask) != 0;
				hasZip = (flags & localMask.zipMask) != 0;
				hasPhone = (flags & localMask.phoneMask) != 0;
				hasHighwayExit = (flags & localMask.highwayExitMask) != 0;
				hasTides = (flags & localMask.tidesMask) != 0;
			} else {
				flags = poiGlobalFlags;

				hasStreetNum = (flags & POIRecord.HAS_STREET_NUM) != 0;
				hasStreet = (flags & POIRecord.HAS_STREET) != 0;
				hasCity = (flags & POIRecord.HAS_CITY) != 0;
				hasZip = (flags & POIRecord.HAS_ZIP) != 0;
				hasPhone = (flags & POIRecord.HAS_PHONE) != 0;
				hasHighwayExit = (flags & POIRecord.HAS_EXIT) != 0;
				hasTides = (flags & POIRecord.HAS_TIDE_PREDICTION) != 0;
			}

			if (hasStreetNum) {
				byte b = reader.get();
				String num = reader.getBase11str(b, '-');
				if (num.isEmpty()) {
					int mpoffset = (b << 16) & 0xff0000;
					mpoffset |= reader.getChar() & 0xffff;

					poi.setComplexPhoneNumber(fetchLabel(mpoffset));
				} else {
					poi.setSimpleStreetNumber(num);
				}
			}

			if (hasStreet) {
				int streetNameOffset = reader.get3();// label for street
				Label label = fetchLabel(streetNameOffset);
				//System.out.println("street " + label.getText());
				poi.setStreetName(label);
			}

			if (hasCity) {
				int cityIndex;

				if (placeHeader.getNumCities() > 0xFF)
					cityIndex = reader.getChar();
				else
					cityIndex = reader.get() & 0xff;
				//System.out.println("city " + cityIndex);
				
				poi.setCity(cities.get(cityIndex-1));
			}

			if (hasZip) {
				int n;
				if (placeHeader.getNumZips() > 0xff)
					n = reader.getChar();
				else
					n = reader.get();
				// TODO save the zip
			}
			
			if (hasPhone) {
				byte b = reader.get();
				String num = reader.getBase11str(b, '-');
				if (num.isEmpty()) {
					// Yes this is a bit strange it is a byte followed by a char
					int mpoffset = (b << 16) & 0xff0000;
					mpoffset |= reader.getChar() & 0xffff;

					Label label = fetchLabel(mpoffset);
					poi.setComplexPhoneNumber(label);
				} else {
					poi.setSimplePhoneNumber(num);
				}
			}

			if (hasHighwayExit) {

				int lblinfo = reader.get3();
				int highwayLabelOffset = lblinfo & 0x3FFFF;
				boolean indexed = (lblinfo & 0x800000) != 0;
				boolean overnightParking = (lblinfo & 0x400000) != 0;

				byte highwayIndex = reader.get();
				if (indexed) {
					int eidx = (placeHeader.getNumExits() > 255) ?
									reader.getChar() :
									reader.get();
				}
			}

			pois.put(poiOffset, poi);
		}
	}

	/**
	 * The meaning of the bits in the local flags depends on which bits
	 * are set in the global flags.  Hence we have to calculate the
	 * masks to use.  These are held in an instance of PoiMasks
	 * @param placeHeader The label header.
	 * @return The masks as modified by the global flags.
	 */
	private PoiMasks makeLocalMask(PlacesHeader placeHeader) {
		int globalPoi = placeHeader.getPOIGlobalFlags();

		char mask= 0x1;

		boolean has_street_num = (globalPoi & POIRecord.HAS_STREET_NUM) != 0;
		boolean has_street = (globalPoi & POIRecord.HAS_STREET) != 0;
		boolean has_city = (globalPoi & POIRecord.HAS_CITY) != 0;
		boolean has_zip = (globalPoi & POIRecord.HAS_ZIP) != 0;
		boolean has_phone = (globalPoi & POIRecord.HAS_PHONE) != 0;
		boolean has_hwyexit = (globalPoi & POIRecord.HAS_EXIT) != 0;
		boolean has_tides = (globalPoi & POIRecord.HAS_TIDE_PREDICTION) != 0;

		PoiMasks localMask = new PoiMasks();

		if ( has_street_num ) {
			localMask.streetNumMask = mask;
			mask <<= 1;
		}

		if ( has_street ) {
			localMask.streetMask = mask;
			mask <<= 1;
		}

		if ( has_city ) {
			localMask.cityMask= mask;
			mask <<= 1;
		}

		if ( has_zip ) {
			localMask.zipMask= mask;
			mask <<= 1;
		}

		if ( has_phone ) {
			localMask.phoneMask= mask;
			mask <<= 1;
		}

		if ( has_hwyexit ) {
			localMask.highwayExitMask = mask;
			mask <<= 1;
		}

		if ( has_tides ) {
			localMask.tidesMask = mask;
			mask <<=1;
		}

		return localMask;
	}

	private String recoverText() {
		EncodedText encText = textDecoder.getText();
		String text;
		try {
			text = new String(encText.getCtext(), 0, encText.getLength(), "utf-8");
		} catch (UnsupportedEncodingException e) {
			// this can't really happen because utf-8 must be supported
			text = "";
		}
		return text;
	}

	private class PoiMasks {
		private char streetNumMask;
		private char streetMask;
		private char cityMask;
		private char zipMask;
		private char phoneMask;
		private char highwayExitMask;
		private char tidesMask;
	}
}
