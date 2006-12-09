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

import java.io.IOException;

/**
 * The file that holds all the labels for the map.
 *
 * @author Steve Ratcliffe
 */
public class LBLFile extends ImgFile {

	private static int HEADER_LEN = 196; // Other lengths are possible
	private static int INFO_LEN = 5;

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
	private static final int ENCODING_10BIT = 10;

	public LBLFile(ImgChannel chan) {
		setHeaderLength(HEADER_LEN);
		setType("GARMIN LBL");

		WriteStrategy writer = new BufferedWriteStrategy(chan);
		setWriteStrategy(writer);

		position(HEADER_LEN + INFO_LEN);
	}

	public void sync() throws IOException {
		position(0);

		writeCommonHeader();
		writeHeader();

		getWriter().sync();
	}

	private void writeHeader() throws IOException {

		putInt(HEADER_LEN);
		putInt(INFO_LEN);

		put((byte) 0);
		put((byte) ENCODING_6BIT);

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
		putChar((char) UNK2_REC_LEN);
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

		putChar((char) 0); //code
		putInt(0);

		putInt(dataPos);
		putInt(0);

		putInt(dataPos);
		putInt(0);
		putChar(UNK3_REC_LEN);
		putChar((char) 0);
	}

	public Label newLabel(String text) {
		Label l = new Label(text);
		l.setOffset(dataPos);
		dataPos += l.getLength();

		return l;
	}
}
