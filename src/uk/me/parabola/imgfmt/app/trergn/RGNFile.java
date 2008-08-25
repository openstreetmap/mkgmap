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
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The region file.  Holds actual details of points and lines etc.
 *
 * 
 *
 * The data is rather complicated and is packed to save space.  This class does
 * not really handle that format however as it is written by the
 * {@link MapObject}s themselves.
 *
 * Each subdivision takes space in this file.  The I am expecting this to be the
 * biggest file, although it seems that TRE may be in some circumstances.
 *
 * @author Steve Ratcliffe
 */
public class RGNFile extends ImgFile {
	private static final Logger log = Logger.getLogger(RGNFile.class);

	private static final int HEADER_LEN = RGNHeader.HEADER_LEN;

	private final RGNHeader header = new RGNHeader();

	private Subdivision currentDivision;
	private int indPointPtrOff;
	private int polylinePtrOff;
	private int polygonPtrOff;

	public RGNFile(ImgChannel chan) {
		setHeader(header);

		setWriter(new BufferedImgFileWriter(chan));

		// Position at the start of the writable area.
		position(HEADER_LEN);
	}

	public void write() {
	}

	public void writePost() {
		header.setDataSize(position() - HEADER_LEN);

		getHeader().writeHeader(getWriter());
	}

	public void startDivision(Subdivision sd) {

		sd.setRgnPointer(position() - HEADER_LEN);

		// We need to reserve space for a pointer for each type of map
		// element that is supported by this division.  Note that these
		// pointers are only 2bytes long.  A pointer to the points is never
		// needed as it will always be first if present.
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

		currentDivision = sd;
	}

	public void addMapObject(MapObject item) {
		item.write(getWriter());
	}

	public void setIndPointPtr() {
		if (currentDivision.needsIndPointPtr()) {
			long currPos = position();
			position(indPointPtrOff);
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			if (off > 0xffff)
				throw new IllegalStateException("Too many items in indexed points section");

			getWriter().putChar((char) off);
			position(currPos);
		}
	}

	public void setPolylinePtr() {
		if (currentDivision.needsPolylinePtr()) {
			long currPos = position();
			position(polylinePtrOff);
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			if (off > 0xffff)
				throw new IllegalStateException("Too many items in polyline section");

			if (log.isDebugEnabled())
				log.debug("setting polyline offset to", off);
			getWriter().putChar((char) off);

			position(currPos);
		}
	}

	public void setPolygonPtr() {
		if (currentDivision.needsPolygonPtr()) {
			long currPos = position();
			long off = currPos - currentDivision.getRgnPointer() - HEADER_LEN;
			log.debug("currpos=" + currPos + ", off=" + off);
			if (off > 0xffff)
				throw new IllegalStateException("Too many items in the polygon section");

			if (log.isDebugEnabled())
				log.debug("setting polygon offset to ", off, " @", polygonPtrOff);
			position(polygonPtrOff);
			getWriter().putChar((char) off);
			position(currPos);
		}
	}

	public ImgFileWriter getWriter() {
		return super.getWriter();
	}
}
