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
 * Each subdivision takes space in this file.  The 
 * I am expecting this to be the biggest file, although it seems that TRE may
 * be in some circumstances.
 *
 * @author Steve Ratcliffe
 */
public class RGNFile extends ImgFile {
	private static final int HEADER_LEN = 29;

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

	private void writeHeader()  {

		putInt(HEADER_LEN);
		putInt(dataSize);
	}

    private int pointPtrOff;
    private int indPointPtrOff;
    private int polylinePtrOff;
    private int polygonPtrOff;

	public void addDivision(Subdivision sd) {

        // We need to reserve space for a pointer for each type of map
        // element that is supported by this division.  Note that these
        // pointers are only 2bytes long
        if (sd.needsPointPtr()) {
            pointPtrOff = position();
            position(position() + 2);
        }

        if (sd.needsIndPointPtr()) {
            indPointPtrOff = position();
            position(position() + 2);
        }

        if (sd.needsPolylinePtr()) {
            polylinePtrOff = position();
            position(position() + 2);
        }

        if (sd.needsPolygonPtr()) {
            polygonPtrOff = position();
            position(position() + 2);
        }


        sd.setRgnPointer(position() - HEADER_LEN);
    }

	public void addMapObject(MapObject item) {
		item.write(this);
	}
}
