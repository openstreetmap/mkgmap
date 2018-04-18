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
package uk.me.parabola.imgfmt.app.lbl;

import java.io.UnsupportedEncodingException;

import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.labelenc.CodeFunctions;
import uk.me.parabola.imgfmt.app.srt.Sort;

/**
 * The header for the LBL file.
 *
 * @author Steve Ratcliffe
 */
public class LBLHeader extends CommonHeader {
	public static final int HEADER_LEN = 196; // Other lengths are possible

	private static final char UNK3_REC_LEN = 0;

	private int labelStart; // Start of labels.
	private int labelSize; // Size of file.

	private int offsetMultiplier;

	// Code page and sorting info.
	private Sort sort;

	private int sortDescriptionLength;

	// The type of encoding employed.  This is not a length.
	private int encodingType = CodeFunctions.ENCODING_FORMAT6;

	// The label section also contains all kinds of records related to place,
	// so these have all been put in their own class.
	private final PlacesHeader placeHeader;

	public LBLHeader() {
		super(HEADER_LEN, "GARMIN LBL");
		placeHeader = new PlacesHeader();
	}

	public int getSortDescriptionLength() {
		return sortDescriptionLength;
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) {
		labelStart = reader.get4();
		labelSize = reader.get4();
		offsetMultiplier = 1 << reader.get();
		encodingType = reader.get();

		// Read the places part of the header.
		placeHeader.readFileHeader(reader);

		int codepage = reader.get2u();
		int id1 = reader.get2u();
		int id2 = reader.get2u();
		int descOff = reader.get4();
		int descLen = reader.get4();

		reader.position(descOff);
		byte[] bytes = reader.get(descLen);
		String description;
		try {
			description = new String(bytes, "ascii");
		} catch (UnsupportedEncodingException e) {
			description = "Unknown";
		}

		sort = new Sort();
		sort.setCodepage(codepage);
		sort.setId1(id1);
		sort.setId2(id2);
		sort.setDescription(description);
		
		// more to do but not needed yet...  Just set position
		reader.position(labelStart);
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		// LBL1 section, these are regular labels
		writer.put4(HEADER_LEN + sortDescriptionLength);
		writer.put4(getLabelSize());

		writer.put1u(offsetMultiplier);
		writer.put1u(encodingType);

		placeHeader.writeFileHeader(writer);

		writer.put2u(getCodePage());

		// Identifying the sort
		int id1 = sort.getId1();
		writer.put2u(id1);
		
		int id2 = sort.getId2();
		if (id1 != 0 && id2 != 0)
			id2 |= 0x8000;
		writer.put2u(id2);

		writer.put4(HEADER_LEN);
		writer.put4(sortDescriptionLength);

		writer.put4(placeHeader.getLastPos());
		writer.put4(0);
		writer.put2u(UNK3_REC_LEN);
		writer.put2u(0);
	}

	protected int getEncodingType() {
		return encodingType;
	}

	public void setEncodingType(int type) {
		this.encodingType = type;
	}

	protected int getLabelSize() {
		return labelSize;
	}

	public void setLabelSize(int labelSize) {
		this.labelSize = labelSize;
		placeHeader.setLabelEnd(HEADER_LEN + sortDescriptionLength + labelSize);
	}

	protected int getCodePage() {
		return sort.getCodepage();
	}

	public void setSort(Sort sort) {
		sortDescriptionLength = sort.getDescription().length() + 1;
		this.sort = sort;
	}

	public int getSortOrderId() {
		return sort.getSortOrderId();
	}

	public int getLabelStart() {
		return labelStart;
	}

	public int getOffsetMultiplier() {
		return offsetMultiplier;
	}

	public PlacesHeader getPlaceHeader() {
		return placeHeader;
	}

	public void setOffsetMultiplier(int offsetMultiplier) {
		this.offsetMultiplier = offsetMultiplier;
	}
}
