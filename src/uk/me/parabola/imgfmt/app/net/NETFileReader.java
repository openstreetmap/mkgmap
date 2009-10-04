/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * Read the NET file.
 */
public class NETFileReader extends ImgFile {
	private final NETHeader netHeader = new NETHeader();

	// To begin with we only need LBL offsets.
	private final Map<Integer, Integer> offsetLabelMap = new HashMap<Integer, Integer>();

	public NETFileReader(ImgChannel chan) {
		setHeader(netHeader);

		setReader(new BufferedImgFileReader(chan));
		netHeader.readHeader(getReader());

		readLabelOffsets();
	}

	/**
	 * Get the label offset, given the NET offset.
	 * @param netOffset An offset into NET 1, as found in the road entries in
	 * RGN for example.
	 * @return The offset into LBL as found in NET 1.
	 */
	public int getLabelOffset(int netOffset) {
		Integer off = offsetLabelMap.get(netOffset);
		if (off == null)
			return 0;
		else
			return off;
	}

	/**
	 * The first field in NET 1 is a label offset in LBL.  Currently we
	 * are only interested in that to convert between a NET 1 offset and
	 * a LBL offset.
	 */
	private  void readLabelOffsets() {
		ImgFileReader reader = getReader();
		List<Integer> offsets = readOffsets();
		int start = netHeader.getRoadDefinitionsStart();
		for (int off : offsets) {
			reader.position(start + off);
			int labelOffset = reader.get3() & 0xffffff;
			//System.out.printf("l off %x\n" , labelOffset);
			offsetLabelMap.put(off, labelOffset & 0x7fffff); // TODO what if top bit is not set?
		}
	}

	/**
	 * NET 3 contains a list of all the NET 1 record start positions.  They
	 * are in alphabetical order of name.  So read them in and sort into
	 * memory address order.
	 * @return A list of start offsets in NET 1, sorted by increasing offset.
	 */
	private List<Integer> readOffsets() {
		int start = netHeader.getSortedRoadsStart();
		int end = netHeader.getSortedRoadsEnd();
		ImgFileReader reader = getReader();
		reader.position(start);

		List<Integer> offsets = new ArrayList<Integer>();
		while (reader.position() < end) {
			int off = reader.get3() & 0xffffff;
			offsets.add(off);
		}

		// Sort in address order in the hope of speeding up reading.
		Collections.sort(offsets);
		return offsets;
	}
}
