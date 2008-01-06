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

import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ReadStrategy;
import uk.me.parabola.imgfmt.app.WriteStrategy;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.lbl.PlacesHeader;
import uk.me.parabola.imgfmt.ReadFailedException;

/**
 * @author Steve Ratcliffe
 */
public class NETHeader extends CommonHeader {
	public static final int HEADER_LEN = 55; // Other lengths are possible

	private static final char SORTED_ROAD_RECSIZE = 3;

	private Section roadDefinitions = new Section();
	private Section segmentedRoads = new Section(roadDefinitions);

	private Section sortedRoads = new Section(segmentedRoads, SORTED_ROAD_RECSIZE);

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
	protected void readFileHeader(ReadStrategy reader) throws ReadFailedException {
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(WriteStrategy writer) {
		writer.putInt(roadDefinitions.getPosition());
		writer.putInt(roadDefinitions.getSize());
		writer.put((byte) 0); // offset multiplier

		writer.putInt(segmentedRoads.getPosition());
		writer.putInt(segmentedRoads.getSize());
		writer.put((byte) 0); // offset multiplier

		writer.putInt(sortedRoads.getPosition());
		writer.putInt(sortedRoads.getSize());
		writer.putInt(sortedRoads.getItemSize());

		writer.putInt(0);
		writer.put((byte) 1);
		writer.put((byte) 0);
	}
}
