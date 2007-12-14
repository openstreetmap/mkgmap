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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app;

/**
 * The header for the LBL file.
 *
 * @author Steve Ratcliffe
 */
public class LBLHeader extends CommonHeader {
	public static final int HEADER_LEN = 196; // Other lengths are possible

	static final int INFO_LEN = 28;

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
	static final int ENCODING_6BIT = 6;
	static final int ENCODING_8BIT = 9;  // Yes it really is 9 apparently

	private int labelSize; // Size of file.
	private int dataPos = HEADER_LEN + INFO_LEN;

	// Code page? may not do anything.
	private int codePage = 850;

	private int encodingLength = ENCODING_6BIT;

	public LBLHeader() {
		super(HEADER_LEN, "GARMIN LBL");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ReadStrategy reader) {
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(WriteStrategy writer) {
		// LBL1 section, these are regular labels
		writer.putInt(HEADER_LEN + INFO_LEN);
		writer.putInt(getLabelSize());

		writer.put((byte) 0);
		writer.put((byte) getEncodingLength());

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(COUNTRY_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(REGION_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(CITY_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(UNK1_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.put((byte) 0);
		writer.put((byte) 0);
		writer.putChar((char) 0);
		writer.put((byte) 0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(UNK2_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(ZIP_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(HIGHWAY_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(EXIT_REC_LEN);
		writer.putInt(0);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(HIGHWAYDATA_REC_LEN);
		writer.putInt(0);

		writer.putChar((char) getCodePage()); //code
		writer.putInt(0);

		// Sort descriptor ???
		writer.putInt(HEADER_LEN);
		writer.putInt(INFO_LEN);

		writer.putInt(getDataPos());
		writer.putInt(0);
		writer.putChar(UNK3_REC_LEN);
		writer.putChar((char) 0);
	}

	public int getEncodingLength() {
		return encodingLength;
	}

	public void setEncodingLength(int encodingLength) {
		this.encodingLength = encodingLength;
	}

	public int getLabelSize() {
		return labelSize;
	}

	public void setLabelSize(int labelSize) {
		this.labelSize = labelSize;
	}

	protected int getDataPos() {
		return dataPos;
	}

	public void setDataPos(int dataPos) {
		this.dataPos = dataPos;
	}

	public int getCodePage() {
		return codePage;
	}

	public void setCodePage(int codePage) {
		this.codePage = codePage;
	}
}
