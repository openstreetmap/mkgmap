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
import java.nio.ByteBuffer;

/**
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

	public LBLFile(ImgChannel chan) {
		super(chan);
		setLength(HEADER_LEN);
		setType("GARMIN LBL");
	}

	public void writeHeader() throws IOException {
		ByteBuffer buf = allocateBuffer();

		buf.putInt(HEADER_LEN);
		buf.putInt(INFO_LEN);

		buf.put((byte) 0);
		buf.put((byte) 0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(COUNTRY_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(REGION_REC_LEN);
		buf.putInt(0);


		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(CITY_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(UNK1_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.put((byte) 0);
		buf.put((byte) 0);
		buf.putChar((char) 0);
		buf.put((byte) 0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar((char) UNK2_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(ZIP_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(HIGHWAY_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(EXIT_REC_LEN);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(HIGHWAYDATA_REC_LEN);
		buf.putInt(0);

		buf.putChar((char) 0); //code
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);

		buf.putInt(dataPos);
		buf.putInt(0);
		buf.putChar(UNK3_REC_LEN);
		buf.putChar((char) 0);

		write(buf);
	}

	protected void writeBody() throws IOException {
	}
}
