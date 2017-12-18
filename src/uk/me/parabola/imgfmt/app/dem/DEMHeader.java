/*
 * Copyright (C) 2017.
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
package uk.me.parabola.imgfmt.app.dem;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The header of the DEM file.
 * 
 * @author Gerd Petermann
 */
public class DEMHeader extends CommonHeader {
	public static final int HEADER_LEN = 41; // Other lengths are possible
	
	private final List<DEMSection> zoomLevels = new ArrayList<>();
	
	private int offset;

	public DEMHeader() {
		super(HEADER_LEN, "GARMIN DEM");
	}

	
	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		int pos = writer.position();
		writer.position(HEADER_LEN);
		for (int i = 0; i < zoomLevels.size(); i++) {
			zoomLevels.get(i).writeRest(writer);
		}
		offset = writer.position();
		for (int i = 0; i < zoomLevels.size(); i++) {
			zoomLevels.get(i).writeHeader(writer);
		}
		writer.position(pos);
		
		writer.putInt(0); // 0: elevation in metres, 1: foot
		writer.put2(zoomLevels.size());
		writer.putInt(0); // unknown
		writer.put2(60); // size of zoom level record
		writer.putInt(offset); // offset to first DemSection header (they appear at the end of the file!)
		writer.putInt(1); // unknown, also 0 spotted
		
	}

	@Override
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
	}


	public void addSection(DEMSection section) {
		zoomLevels.add(section);
	}

}
