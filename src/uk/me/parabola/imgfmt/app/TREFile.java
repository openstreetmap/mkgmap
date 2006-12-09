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
import uk.me.parabola.imgfmt.Utils;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;

/**
 * This is the file that contains the overview of the map.  There
 * can be different zoom levels and each level of zoom has an
 * associated set of subdivided areas.  Each area then points
 * into the RGN file.
 *
 * @author Steve Ratcliffe
 */
public class TREFile extends ImgFile {
	static private Logger log = Logger.getLogger(TREFile.class);

	private static int HEADER_LEN = 116; // Other values are possible
	private static int INFO_LEN = 50;

	private int dataPos = HEADER_LEN + INFO_LEN;

	// Bounding box.  All units are in map units.
	private Bounds bounds;

	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;
	private static final char COPYRIGHT_REC_SIZE = 0x3;

	// Zoom levels for map
	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private List<Label> copyrights = new ArrayList<Label>();
	private int copyrightPos;
	private int copyrightSize;

	private byte poiDisplayFlags;
	private int polylineSize;
	private int polygonSize;
	private int pointSize;

	public TREFile(ImgChannel chan) {
		setHeaderLength(HEADER_LEN);
		setType("GARMIN TRE");
		setWriter(new BufferedWriteStrategy(chan));

		// Position at the start of the writable area.
		position(HEADER_LEN + INFO_LEN);
	}

	public void sync() throws IOException {
		// Do anything that is in structures and that needs to be dealt with.
		prepare();

		// Now refresh the header
		position(0);
		writeCommonHeader();
		writeHeader();

		getWriter().sync();
	}

	/**
	 * Set the bounds based upon the latitude and longitude in degrees.
	 * @param area The area bounded by the map.
	 */
	public void setBounds(Bounds area) {
		this.bounds = area;
	}

	public Zoom createZoom(int zoom, int bits) {
		Zoom z = new Zoom(zoom, bits);
		return z;
	}

	public void addCopyright(Label cr) {
		copyrights.add(cr);
	}

	/**
	 * Anything waiting to be written is dealt with here.
	 */
	private void prepare() {
		// Write out the pointers to the labels that hold the copyright strings
		copyrightPos = position();
		for (Label l : copyrights) {
			copyrightSize += COPYRIGHT_REC_SIZE;
			put3(l.getOffset());
		}
	}

	private void writeHeader() throws IOException {

		put3(bounds.getMaxLat());
		put3(bounds.getMaxLong());
		put3(bounds.getMinLat());
		put3(bounds.getMinLong());

		putInt(mapLevelPos);
		putInt(mapLevelsSize);
		dataPos += mapLevelsSize;

		putInt(dataPos);
		putInt(subdivSize);
		dataPos += subdivSize;

		putInt(dataPos);
		putInt(copyrightSize);
		dataPos += copyrightSize;

		putChar(COPYRIGHT_REC_SIZE);

		putInt(0);

		put(poiDisplayFlags);

		put3(0x19);
		putInt(0x01040d);

		putChar((char) 1);
		put((byte) 0);

		putInt(dataPos);
		putInt(polylineSize);
		putChar(POLYLINE_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		putInt(dataPos);
		putInt(polygonSize);
		putChar(POLYGON_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		putInt(dataPos);
		putInt(pointSize);
		putChar(POINT_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		put(Utils.toBytes("My OSM Map"));

		//int n = write(buf);
		//log.debug("wrote " + n + " bytes for TRE header");
	}


}
