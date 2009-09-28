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
	private static final Label NULL_LABEL = new Label("");

	private CharacterDecoder textDecoder = CodeFunctions.getDefaultDecoder();

	private final Map<String, Label> labelCache = new HashMap<String, Label>();

	private final LBLHeader header = new LBLHeader();

	private final PlacesFile places = new PlacesFile();

	private final Map<Integer, Label> labels = new HashMap<Integer, Label>();

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

		int hl = getHeader().getHeaderLength();
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
		if (label == null) // TODO this is a problem with the 6 byte decoder in that you don't know the actual offset
			label = labels.get(offset-1);
		if (label == null)
			return NULL_LABEL;
		else
			return label;
	}

	/**
	 * Get the string associated with a label, given the labels offset value.
	 * @param offset Offset in the file.  These offsets are used in the other
	 * map files, such as RGN and NET.
	 * @return The label as a string.  Will be an empty string if there is no
	 * text for the label.  Note that this is particularly the case when the
	 * offset is zero.
	 */
	public String fetchLabelString(int offset) {
		// Short cut the simple case of no label
		if (offset == 0)
			return "";  // or null ???

		Label l= labels.get(offset);
		if (l == null)
			return "";
		else
			return l.getText();
	}

	public PlacesHeader getPlaceHeader() {
		return header.getPlaceHeader();
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