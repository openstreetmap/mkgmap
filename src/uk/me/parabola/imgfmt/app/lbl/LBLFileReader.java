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
	private final Map<Integer, Label> pois = new HashMap<Integer, Label>();
	private final Map<Integer, Country> countries = new HashMap<Integer, Country>();
	private final Map<Integer, Region> regions = new HashMap<Integer, Region>();


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
		readPoiInfo();

		readCountries();
		readRegions();
	}

	/**
	 * Get a label by its offset in the label area.
	 * @param offset The offset in the label section.  The offset 0 always
	 * is an empty string.
	 * @return The label including its text.
	 */
	public Label fetchLabel(int offset) {
		Label label = labels.get(offset);
		if (label == null) // TODO this is a problem with the 6 byte decoder in that the actual offset could be one behind
			label = labels.get(offset-1);
		if (label == null)
			return NULL_LABEL;
		else
			return label;
	}

	/**
	 * Get a list of cites.  This is not cached here.
	 * @return A list of City objects.
	 */
	public List<City> getCities() {
		List<City> cities = new ArrayList<City>();

		PlacesHeader placeHeader = header.getPlaceHeader();
		int start = placeHeader.getCitiesStart();
		int end = placeHeader.getCitiesEnd();

		ImgFileReader reader = getReader();

		int index = 1;
		reader.position(start);
		while (reader.position() < end) {
			// First is either a label offset or a point/subdiv combo, we
			// don't know until we have read further
			int label = reader.get3();

			boolean isPointRef = false, regionIsCountry= false;
			int info = reader.getChar();
			if ((info & 0x8000) != 0) {
				// Has subdiv/point index
				int pointIndex = label & 0xff;
				int subdiv = (label >> 8) & 0xffff;

				System.out.printf("city subdiv %x, idxpoint %d\n", subdiv, pointIndex);
				City city;
				if ((info & 0x4000) != 0) {
					Country country = countries.get(info & 0x3fff);
					city = new City(country);
				} else {
					Region region = regions.get(info & 0x3fff);
					city = new City(region);
				}

				city.setIndex(index);
				city.setPointIndex((byte) pointIndex);
				city.setSubdivision(Subdivision.createEmptySubdivision(subdiv));
				cities.add(city);
			} // else it has a label but that isn't much use for the index and so we ignore them

			index++;
		}

		return cities;
	}

	/**
	 * Return POI information.
	 * @param offset The offset of the poi information in the header.
	 * @return Currently just a string, later a POIRecord with all information
	 * in it. TODO actually return POI :)
	 */
	public Label fetchPoi(int offset) {
		Label s = pois.get(offset);
		if (s == null)
			s = NULL_LABEL;
		return s;
	}

	/**
	 * Read a cache the countries. These are used when reading cities.
	 */
	private void readCountries() {
		ImgFileReader reader = getReader();

		PlacesHeader placeHeader = header.getPlaceHeader();

		int start = placeHeader.getCountriesStart();
		int end = placeHeader.getCountriesEnd();

		int index = 1;
		reader.position(start);
		while (reader.position() < end) {
			int offset = reader.get3();
			Label label = fetchLabel(offset);

			if (label != null) {
				System.out.printf("country %d: %s\n", index, label.getText());
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

		int index = 1;
		reader.position(start);
		while (reader.position() < end) {
			int country = reader.getChar();
			int offset = reader.get3();
			Label label = fetchLabel(offset);
			if (label != null) {
				System.out.printf("region %d: %s c=%d\n", index, label.getText(), country);
				Region region = new Region(countries.get(country));
				region.setIndex(index);
				region.setLabel(label);

				regions.put(index, region);
			}

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

		int poiOffset;
		reader.position(start);

		int lblinfo;
		boolean override;

		PoiMasks localMask = makeLocalMask(placeHeader);

		while (position() < end) {
			poiOffset = position() - start;
			int val = reader.get3();
			int labelOffset = val & 0x3fffff;

			override = (val & 0x800000) != 0;

			// We have what we want, but now have to find the start of the
			// next record as they are not fixed length.
			int flags;
			boolean hasStreet = false;
			boolean hasStreetNum = false;
			boolean hasCity = false;
			boolean hasZip = false;
			boolean hasPhone = false;
			boolean hasHighwayExit = false;
			boolean hasTides = false;
			boolean hasUnkn = false;

			if (override) {
				flags = reader.get();
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

			if (override) {
				hasStreetNum = (flags & localMask.streetNumMask) != 0;
				hasStreet = (flags & localMask.streetMask) != 0;
				hasCity = (flags & localMask.cityMask) != 0;
				hasZip = (flags & localMask.zipMask) != 0;
				hasPhone = (flags & localMask.phoneMask) != 0;
				hasHighwayExit = (flags & localMask.highwayExitMask) != 0;
				hasTides = (flags & localMask.tidesMask) != 0;
			}

			if (hasStreetNum) {
				fetchBase11(reader);
			}

			if (hasStreet) {
				reader.get3();  // label for street
			}

			if (hasCity) {
				int cidx;

				if (placeHeader.getNumCities() > 0xFF)
					cidx = reader.getChar();
				else
					cidx = reader.get();
			}

			if (hasPhone) {
				String phone = fetchBase11(reader);
			}

			if (hasHighwayExit) {
				boolean hasEidx, hasOnpark;
				String ehas;

				lblinfo = reader.get3();
				int highwayLabelOffset = lblinfo & 0x3FFFF;
				hasOnpark = (lblinfo & 0x400000) != 0;
				hasEidx = (lblinfo & 0x800000) != 0;

				if (hasEidx) {
					int eidx =
							(placeHeader.getNumExits() > 255) ?
									reader.getChar() :
									reader.get();
				}
			}

			// Just save the label for now.  Later on will save the whole
			// poi
			pois.put(poiOffset, fetchLabel(labelOffset));
		}
	}

	/**
	 * Fetch a base 11 quantity.  If there is not one, then fetch a label
	 * instead.
	 * @param reader A reader correctly positioned at the start of the number
	 * or label reference.
	 */
	private String fetchBase11(ImgFileReader reader) {
		String num = reader.getBase11str('-');
		if (num.isEmpty()) {
			int mpoffset;
			int idx;

			mpoffset = reader.get() << 16;
			mpoffset |= reader.getInt();
			num = fetchLabel(mpoffset).getText();
		}
		return num;
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

		boolean has_street;
		boolean has_street_num;
		boolean has_city;
		boolean has_zip;
		boolean has_phone;
		boolean has_hwyexit;
		boolean has_tides;
		boolean has_unkn;

		char mask= 0x1;

		has_street_num = (globalPoi & POIRecord.HAS_STREET_NUM) != 0;
		has_street = (globalPoi & POIRecord.HAS_STREET) != 0;
		has_city = (globalPoi & POIRecord.HAS_CITY) != 0;
		has_zip = (globalPoi & POIRecord.HAS_ZIP) != 0;
		has_phone = (globalPoi & POIRecord.HAS_PHONE) != 0;
		has_hwyexit = (globalPoi & POIRecord.HAS_EXIT) != 0;
		has_tides = (globalPoi & POIRecord.HAS_TIDE_PREDICTION) != 0;

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


	//public PlacesHeader getPlaceHeader() {
	//	return header.getPlaceHeader();
	//}
	//
	//public int numCities() {
	//	return places.numCities();
	//}
	//
	//public int numZips() {
	//	return places.numZips();
	//}
	//
	//public int numHighways() {
	//	return places.numHighways();
	//}
	//
	//public int numExitFacilities() {
	//	return places.numExitFacilities();
	//}

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
