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
import java.nio.ByteBuffer;

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
	private int minLat = Utils.toMapUnit(51.55109803705159);
	private int maxLat = Utils.toMapUnit(51.56378751797807);
	private int maxLong = Utils.toMapUnit(-0.21497659409451947);
	private int minLong = Utils.toMapUnit(-0.24594693766998674);
	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;

	private int mapLevelsSize;
	private int subdivSize;
	private int copyrightSize;

	private byte poiDisplayFlags;
	private int polylineSize;
	private int polygonSize;
	private int pointSize;
	private static final char COPYRIGHT_REC_SIZE = 0x3;

	public TREFile(ImgChannel chan) {
		super(chan);
		setLength(HEADER_LEN);
		setType("GARMIN TRE");
	}

	/**
	 * Set the bounding box for this map.
	 *
	 * @param minLat South boundry.
	 * @param minLong East boundry.
	 * @param maxLat North boundry.
	 * @param maxLong West boundry.
	 */
	public void setBounds(int minLat, int minLong, int maxLat, int maxLong) {
		this.minLat = minLat;
		this.minLong = minLong;
		this.maxLat = maxLat;
		this.maxLong = maxLong;
	}

	protected void writeHeader() throws IOException {
		ByteBuffer buf = allocateBuffer();

		put3(buf, maxLat);
		put3(buf, maxLong);
		put3(buf, minLat);
		put3(buf, minLong);

		buf.putInt(dataPos);
		buf.putInt(mapLevelsSize);
		dataPos += mapLevelsSize;

		buf.putInt(dataPos);
		buf.putInt(subdivSize);
		dataPos += subdivSize;

		buf.putInt(dataPos);
		buf.putInt(copyrightSize);
		dataPos += copyrightSize;

		buf.putChar(COPYRIGHT_REC_SIZE);

		buf.putInt(0);

		buf.put(poiDisplayFlags);

		put3(buf, 0x19);
		buf.putInt(0x01040d);

		buf.putChar((char) 1);
		buf.put((byte) 0);

		buf.putInt(dataPos);
		buf.putInt(polylineSize);
		buf.putChar((char) POLYLINE_REC_LEN);

		buf.putChar((char) 0);
		buf.putChar((char) 0);

		buf.putInt(dataPos);
		buf.putInt(polygonSize);
		buf.putChar(POLYGON_REC_LEN);

		buf.putChar((char) 0);
		buf.putChar((char) 0);

		buf.putInt(dataPos);
		buf.putInt(pointSize);
		buf.putChar((char) POINT_REC_LEN);

		buf.putChar((char) 0);
		buf.putChar((char) 0);

		buf.put(Utils.toBytes("My OSM Map"));

		int n = write(buf);
		log.debug("wrote " + n + " bytes for TRE header");
	}

}
