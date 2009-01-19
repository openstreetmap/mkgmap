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
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * The header for the RGN file.  This is very simple, just a location and size.
 *
 * @author Steve Ratcliffe
 */
public class RGNHeader extends CommonHeader {
	private static final int DEF_LEN = 29;
	//private static final int DEF_LEN = 125;
	public static final int HEADER_LEN = DEF_LEN;

	private int dataOffset;
	private int dataSize;
	
	private final Section rgn2 = new Section();
	private final Section rgn3 = new Section(rgn2);
	private final Section rgn4 = new Section(rgn3);

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
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		dataOffset = reader.getInt();
		dataSize = reader.getInt();
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putInt(dataOffset);
        writer.putInt(getDataSize());
		if (getHeaderLength() > 29) {
			rgn2.setPosition(dataOffset + dataSize);
			rgn2.writeSectionInfo(writer);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			rgn3.writeSectionInfo(writer);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);

			rgn4.writeSectionInfo(writer);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
			writer.putInt(0);
		}
	}

	public int getDataOffset() {
		return dataOffset;
	}

	public void setDataOffset(int dataOffset) {
		this.dataOffset = dataOffset;
	}

	protected int getDataSize() {
		return dataSize;
	}

	public void setDataSize(int dataSize) {
		this.dataSize = dataSize;
	}
}