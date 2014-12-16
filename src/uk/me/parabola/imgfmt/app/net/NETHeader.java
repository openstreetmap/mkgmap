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
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.SectionWriter;

/**
 * The header of the NET file.
 * 
 * @author Steve Ratcliffe
 */
public class NETHeader extends CommonHeader {
	public static final int HEADER_LEN = 55; // Other lengths are possible

	private static final char SORTED_ROAD_RECSIZE = 3;

	private final Section roadDefinitions = new Section();
	private final Section segmentedRoads = new Section(roadDefinitions);
	private final Section sortedRoads = new Section(segmentedRoads, SORTED_ROAD_RECSIZE);

	private byte roadShift;
	private byte segmentShift;

	public NETHeader() {
		super(HEADER_LEN, "GARMIN NET");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		roadDefinitions.readSectionInfo(reader, false);
		roadShift = reader.get();

		segmentedRoads.readSectionInfo(reader, false);
		segmentShift = reader.get();

		sortedRoads.readSectionInfo(reader, true);

		reader.getInt();
		reader.get();
		reader.get();
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		roadDefinitions.writeSectionInfo(writer);

		writer.put(roadShift); // offset multiplier

		segmentedRoads.writeSectionInfo(writer);

		writer.put(segmentShift); // offset multiplier

		sortedRoads.writeSectionInfo(writer);

		writer.putInt(0);
		writer.put((byte) 1);
		writer.put((byte) 0);
	}

	ImgFileWriter makeRoadWriter(ImgFileWriter writer) {
		roadDefinitions.setPosition(writer.position());
		return new SectionWriter(writer, roadDefinitions);
	}

	ImgFileWriter makeSortedRoadWriter(ImgFileWriter writer) {
		sortedRoads.setPosition(writer.position());
		return new SectionWriter(writer, sortedRoads);
	}

	public int getRoadDefinitionsStart() {
		return roadDefinitions.getPosition();
	}

	public int getSortedRoadsStart() {
		return sortedRoads.getPosition();
	}

	public int getSortedRoadsEnd() {
		return sortedRoads.getEndPos();
	}

	public int getRoadShift() {
		return roadShift & 0xff;
	}
}
