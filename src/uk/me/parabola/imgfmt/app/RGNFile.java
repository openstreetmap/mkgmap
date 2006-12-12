/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.IOException;

/**
 * The region file.  Holds actual details of points and lines etc.
 *
 * The header is very simple, just a location and size.
 *
 * The data is rather complicated and is packed to save space.  This class does
 * not really handle that format however as it is written by the {@link MapObject}s
 * themselves.
 *
 * I am expecting this to be the biggest file, although it seems that TRE may
 * be in some circumstances.
 *
 * @author Steve Ratcliffe
 */
public class RGNFile extends ImgFile {
	private static int HEADER_LEN = 29;

	private int dataSize;

	public RGNFile(ImgChannel chan) {
		setHeaderLength(HEADER_LEN);
		setType("GARMIN RGN");
		setWriter(new BufferedWriteStrategy(chan));

		// Position at the start of the writable area.
		position(HEADER_LEN);
	}

	public void sync() throws IOException {
		dataSize = position() - HEADER_LEN;

		position(0);
		writeCommonHeader();
		writeHeader();

		getWriter().sync();
	}

	private void writeHeader() throws IOException {

		putInt(HEADER_LEN);
		putInt(dataSize);
	}

	public void addDivision(Subdivision sd) {
		sd.setRgnPointer(position() - HEADER_LEN);
		// XXX to do the index pointers.
	}

	public void addMapObject(MapObject item) {
		item.write(this);
	}
}
