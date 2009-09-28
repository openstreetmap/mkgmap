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
import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.labelenc.CharacterDecoder;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.app.labelenc.EncodedText;
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
				String snum = reader.getBase11str('-');
				if (snum.isEmpty()) {
					int mpoffset;
					int idx;

					mpoffset = reader.get() << 16;
					mpoffset |= reader.getInt();
					snum = fetchLabel(mpoffset).getText();
				}
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
				String phone = reader.getBase11str('-');
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
	 * Return POI information.
	 * @param offset The offset of the poi information in the header.
	 * @return Currently just a string, later a POIRecord with all information
	 * in it.
	 */
	public Label fetchPoi(int offset) {
		Label s = pois.get(offset);
		if (s == null)
			s = NULL_LABEL;
		return s;
	}

	///**
	// * Get the string associated with a label, given the labels offset value.
	// * @param offset Offset in the file.  These offsets are used in the other
	// * map files, such as RGN and NET.
	// * @return The label as a string.  Will be an empty string if there is no
	// * text for the label.  Note that this is particularly the case when the
	// * offset is zero.
	// */
	//public String fetchLabelString(int offset) {
	//	// Short cut the simple case of no label
	//	if (offset == 0)
	//		return "";  // or null ???
	//
	//	Label l= labels.get(offset);
	//	if (l == null)
	//		return "";
	//	else
	//		return l.getText();
	//}
	//
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
