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

import uk.me.parabola.log.Logger;

/**
 * The region file.  Holds actual details of points and lines etc.
 *
 * The header is very simple, just a location and size.
 *
 * The data is rather complicated and is packed to save space.  This class does
 * not really handle that format however as it is written by the {@link
 * MapObject}s themselves.
 *
 * Each subdivision takes space in this file.  The I am expecting this to be the
 * biggest file, although it seems that TRE may be in some circumstances.
 *
 * @author Steve Ratcliffe
 */
public class RGNFile extends ImgFile {
	private static final Logger log = Logger.getLogger(RGNFile.class);

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

	private void writeHeader() {

		putInt(HEADER_LEN);
		putInt(dataSize);
	}

	private Subdivision currentDivision;
	private int pointPtrOff;
	private int indPointPtrOff;
	private int polylinePtrOff;
	private int polygonPtrOff;

	public void startDivision(Subdivision sd) {

		sd.setRgnPointer(position() - HEADER_LEN);

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
			log.debug("the pg ptr off=" + polygonPtrOff);
			position(position() + 2);
		}


		currentDivision = sd;
	}

	public void addMapObject(MapObject item) {
		item.write(this);
	}

	public void setIndPointPtr() {
		if (currentDivision.needsIndPointPtr()) {
			long currPos = position();
			position(indPointPtrOff);
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			if (off > 0xffff) {
				throw new IllegalStateException(
						"Too many items in indexed points section");
			}
			putChar((char) off);
			position(currPos);
		}
	}

	public void setPolylinePtr() {
		if (currentDivision.needsPolylinePtr()) {
			long currPos = position();
			position(polylinePtrOff);
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			if (off > 0xffff) {
				throw new IllegalStateException(
						"Too many items in polyline section");
			}
			log.debug("setting polyline offset to " + off);
			putChar((char) off);

			position(currPos);
		}
	}

	public void setPolygonPtr() {
		if (currentDivision.needsPolygonPtr()) {
			long currPos = position();
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			log.debug("currpos=" + currPos + ", off=" + off);
			if (off > 0xffff) {
				throw new IllegalStateException(
						"Too many items in the polygon section");
			}
			log.debug("setting polygon offset to " + off + " @" + polygonPtrOff);
			position(polygonPtrOff);
			putChar((char) off);
			position(currPos);
		}
	}
}
