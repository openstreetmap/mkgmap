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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.mkgmap.general.LevelInfo;

/**
 * The DEM file. This consists of information about elevation. It is used for hill shading
 * and to calculation the "ele" values in gpx tracks. Based on work of Frank Stinner. 
 *
 * @author Gerd Petermann
 */
public class DEMFile extends ImgFile {
	private final DEMHeader demHeader = new DEMHeader();

	public DEMFile(ImgChannel chan, boolean write) {
		setHeader(demHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(DEMHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			demHeader.readHeader(getReader());
		}
	}

	public void calc(LevelInfo[] levelInfos, Area area) {
		DEMSection section = new DEMSection(0, area, 3312, 3312); // TODO 
		demHeader.addSection(section);
	}
	public void write() {
		getHeader().writeHeader(getWriter());
	}
}
