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
import uk.me.parabola.log.Logger;

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
	private static final Logger log = Logger.getLogger(LBLFileReader.class);

	private CharacterDecoder textDecoder = CodeFunctions.getDefaultDecoder();

	private final Map<String, Label> labelCache = new HashMap<String, Label>();

	private final LBLHeader lblHeader = new LBLHeader();

	private final PlacesFile places = new PlacesFile();

	public LBLFileReader(ImgChannel chan) {
		setHeader(lblHeader);

		setReader(new BufferedImgFileReader(chan));
		lblHeader.readHeader(getReader());
		CodeFunctions funcs = CodeFunctions.createEncoderForLBL(
				lblHeader.getEncodingType());
		textDecoder = funcs.getDecoder();

		// TODO read the places file
		//places.init(this, lblHeader.getPlaceHeader());
	}


	/**
	 * Bit of a shortcut to get a text string from the label file given its
	 * offset.
	 * @param offset Offset in the file.  These offsets are used in the other
	 * map files, such as RGN and NET.
	 * @return The label as a string.  Will be an empty string if there is no
	 * text for the label.  Note that this is particularly the case when the
	 * offset is zero.
	 */
	public String fetchLableString(int offset) {
		// Short cut the simple case of no label
		if (offset == 0)
			return "";  // or null ???

		ImgFileReader reader = getReader();
		reader.position(lblHeader.getLabelStart() + offset);

		byte b;
		do {
			b = reader.get();
		} while (!textDecoder.addByte(b)) ;

		EncodedText text = textDecoder.getText();
		return new String(text.getCtext(), 0, text.getLength());
	}

	public PlacesHeader getPlaceHeader() {
		return lblHeader.getPlaceHeader();
	}

	public int numCities() {
		return places.numCities();
	}

	public int numZips() {
		return places.numZips();
	}

	public int numHighways() {
		return places.numHighways();
	}

	public int numExitFacilities() {
		return places.numExitFacilities();
	}
}