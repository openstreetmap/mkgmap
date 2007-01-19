/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.labelenc.Bit10Encoder;
import uk.me.parabola.imgfmt.app.labelenc.CharacterEncoder;
import uk.me.parabola.imgfmt.app.labelenc.EncodedText;
import uk.me.parabola.imgfmt.app.labelenc.Format6Encoder;
import uk.me.parabola.imgfmt.app.labelenc.Latin1Encoder;
import uk.me.parabola.imgfmt.app.labelenc.Simple8Encoder;
import uk.me.parabola.log.Logger;

import java.io.IOException;

/**
 * The file that holds all the labels for the map.
 *
 * Would be quite simple, but there are a number of sections that hold country,
 * region, city, etc. records.
 *
 * To begin with I shall only support regular labels.
 *
 * @author Steve Ratcliffe
 */
public class LBLFile extends ImgFile {
	private static final Logger log = Logger.getLogger(LBLFile.class);

	private static final int HEADER_LEN = 196; // Other lengths are possible
	private static final int INFO_LEN = 28;

	private int labelSize; // Size of file.
	private int dataPos = HEADER_LEN + INFO_LEN;

	private static final char COUNTRY_REC_LEN = 3;
	private static final char REGION_REC_LEN = 5;
	private static final char CITY_REC_LEN = 5;
	private static final char UNK1_REC_LEN = 4;
	private static final char UNK2_REC_LEN = 4;
	private static final char UNK3_REC_LEN = 0;
	private static final char ZIP_REC_LEN = 3;
	private static final char HIGHWAY_REC_LEN = 6;
	private static final char EXIT_REC_LEN = 5;
	private static final char HIGHWAYDATA_REC_LEN = 3;

	// Label encoding length
	private static final int ENCODING_6BIT = 6;
	private static final int ENCODING_8BIT = 9;  // Yes it really is 9 apparently
	private static final int ENCODING_10BIT = 11;

	private int encodingLength = ENCODING_6BIT;
	private CharacterEncoder textEncoder = new Format6Encoder();

	// Code page? may not do anything.
	private int codePage = 850;

	public LBLFile(ImgChannel chan) {
		setHeaderLength(HEADER_LEN);
		setType("GARMIN LBL");

		WriteStrategy writer = new BufferedWriteStrategy(chan);
		setWriteStrategy(writer);

		position(HEADER_LEN + INFO_LEN);

		// The zero offset is for no label.
		put((byte) 0);
	}

	public void sync() throws IOException {
		log.debug("syncing lbl file");

		dataPos = position();

		// Reposition to re-write the header with all updated values.
		position(0);
		writeCommonHeader();
		writeHeader();

		put(Utils.toBytes("Some text for the label gap"));
		
		// Sync our writer.
		getWriter().sync();
	}

	public void setCharacterType(String cs) {
		if ("latin1".equals(cs)) {
			encodingLength = ENCODING_8BIT;
			textEncoder = new Latin1Encoder();

		} else if ("latin2".equals(cs)) {
			encodingLength = ENCODING_8BIT;
//			textEncoder = new Latin2Encoder();
			textEncoder = new Format6Encoder();

		} else if ("bit10".equals(cs)) {
			encodingLength = ENCODING_10BIT;
			textEncoder = new Bit10Encoder();

		} else if ("simple8".equals(cs)) {
			encodingLength = ENCODING_8BIT;
			textEncoder = new Simple8Encoder();

		} else {
			encodingLength = ENCODING_6BIT;
			textEncoder = new Format6Encoder();
		}
	}
	
	/**
	 * Add a new label with the given text.
	 *
	 * @param text The text of the label, it will be uppercased.
	 * @return A reference to the created label.
	 */
	public Label newLabel(String text) {
		Label l;
		EncodedText etext = textEncoder.encodeText(text);
		l = new Label(etext);

		l.setOffset(position() - (HEADER_LEN+INFO_LEN));
		l.write(this);

		labelSize += l.getLength();

		return l;
	}

	public void setCodePage(int codePage) {
		this.codePage = codePage;
	}

	private void writeHeader()  {

		// LBL1 section, these are regular labels
		putInt(HEADER_LEN + INFO_LEN);
		putInt(labelSize);

		put((byte) 0);
		put((byte) encodingLength);

		putInt(dataPos);
		putInt(0);
		putChar(COUNTRY_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(REGION_REC_LEN);
		putInt(0);


		putInt(dataPos);
		putInt(0);
		putChar(CITY_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(UNK1_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		put((byte) 0);
		put((byte) 0);
		putChar((char) 0);
		put((byte) 0);

		putInt(dataPos);
		putInt(0);
		putChar(UNK2_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(ZIP_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(HIGHWAY_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(EXIT_REC_LEN);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(HIGHWAYDATA_REC_LEN);
		putInt(0);

		putChar((char) codePage); //code
		putInt(0);

		// Sort descriptor ???
		putInt(HEADER_LEN);
		putInt(INFO_LEN);

		putInt(dataPos);
		putInt(0);
		putChar(UNK3_REC_LEN);
		putChar((char) 0);
	}
}
