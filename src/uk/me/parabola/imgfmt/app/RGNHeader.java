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

import uk.me.parabola.imgfmt.ReadFailedException;

import java.io.IOException;

/**
 * The header for the RGN file.  This is very simple, just a location and size.
 *
 * @author Steve Ratcliffe
 */
public class RGNHeader extends CommonHeader {
	public static final int HEADER_LEN = 29;

	private int dataOffset;
	private int dataSize;
	
	public RGNHeader() {
		super(HEADER_LEN, "GARMIN RGN");
		dataOffset = HEADER_LEN;
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ReadStrategy reader) throws ReadFailedException {
		dataOffset = reader.getInt();
		dataSize = reader.getInt();
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(WriteStrategy writer) {
		writer.putInt(dataOffset);
        writer.putInt(getDataSize());
	}

	public int getDataOffset() {
		return dataOffset;
	}

	public void setDataOffset(int dataOffset) {
		this.dataOffset = dataOffset;
	}

	public int getDataSize() {
		return dataSize;
	}

	public void setDataSize(int dataSize) {
		this.dataSize = dataSize;
	}
}