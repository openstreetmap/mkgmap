/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 06-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * @author Steve Ratcliffe
 */
public class NODHeader extends CommonHeader {
	public static final int HEADER_LEN = 63;

	static final char DEF_ALIGN = 6;
	private static final char BOUNDARY_ITEM_SIZE = 9;

	private final Section nodes = new Section();
	private final Section roads = new Section(nodes);
	private final Section boundary = new Section(roads, BOUNDARY_ITEM_SIZE);

	private final char align = DEF_ALIGN;

	public NODHeader() {
		super(HEADER_LEN, "GARMIN NOD");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		nodes.setPosition(HEADER_LEN);
		nodes.writeSectionInfo(writer);

		// now sets 0x02 (enable turn restrictions?)
		writer.putInt(0x27);

		writer.putChar(align);
		writer.putChar((char) (align - 1));

		roads.writeSectionInfo(writer);
		writer.putInt(0);

		boundary.writeSectionInfo(writer);
	}

	public void setNodeStart(int start) {
		nodes.setPosition(start);
	}

	public void setNodeSize(int size) {
		nodes.setSize(size);
	}

	public Section getNodeSection() {
		return nodes;
	}

	public void setRoadStart(int start) {
		roads.setPosition(start);
	}

	public void setRoadSize(int size) {
		roads.setSize(size);
	}

	public Section getRoadSection() {
		return roads;
	}

	public void setBoundaryStart(int start) {
		boundary.setPosition(start);
	}

	public void setBoundarySize(int size) {
		boundary.setSize(size);
	}

	public Section getBoundarySection() {
		return boundary;
	}
}
