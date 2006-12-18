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
import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * This is the file that contains the overview of the map.  There
 * can be different zoom levels and each level of zoom has an
 * associated set of subdivided areas.  Each of these areas then points
 * into the RGN file.
 *
 * This is quite a complex file as there are quite a few miscellaneous pieces
 * of information stored.
 *
 * @author Steve Ratcliffe
 */
public class TREFile extends ImgFile {
	static private Logger log = Logger.getLogger(TREFile.class);

	private static int HEADER_LEN = 120; // Other values are possible

	// Bounding box.  All units are in map units.
	private Area area;

	private static final int MAP_LEVEL_REC_SIZE = 4;
	private static final char POLYLINE_REC_LEN = 2;
	private static final char POLYGON_REC_LEN = 2;
	private static final char POINT_REC_LEN = 3;
	private static final char COPYRIGHT_REC_SIZE = 0x3;

	private int mapInfoSize;

	// Zoom levels for map
	//	private List<Zoom> mapLevels = new ArrayList<Zoom>();
	private Zoom[] mapLevels = new Zoom[16];
	private int mapLevelPos;
	private int mapLevelsSize;

	private int subdivPos;
	private int subdivSize;

	private List<Label> copyrights = new ArrayList<Label>();
	private int copyrightPos;
	private int copyrightSize;

	private byte poiDisplayFlags;

	// Information about polylines.  eg roads etc.
	private List<Overview> polylineOverviews = new ArrayList<Overview>();
	private int polylinePos;
	private int polylineSize;

	private int polygonSize;

	private List<Overview> pointOverviews = new ArrayList<Overview>();
	private int pointPos;
	private int pointSize;

	private int mapId;
	private int lastRgnPos;

	private static final int SUBDIV_REC_SIZE = 14;
	private static final int SUBDIV_REC_SIZE2 = 16;

	public TREFile(ImgChannel chan) {
		setHeaderLength(HEADER_LEN);
		setType("GARMIN TRE");
		setWriter(new BufferedWriteStrategy(chan));

		// Position at the start of the writable area.
		position(HEADER_LEN + mapInfoSize);
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
	public void setBounds(Area area) {
		this.area = area;
	}

	public Zoom createZoom(int zoom, int bits) {
		Zoom z = new Zoom(zoom, bits);
		mapLevels[zoom] = z;
		return z;
	}

	/**
	 * Add a string to the 'mapinfo' section.  This is a section between the
	 * header and the start of the data.  Nothing points to it directly.
	 *
	 * @param msg A string, usually used to describe the program that generated
	 * the file.
	 */
	public void addInfo(String msg) {
		byte[] val = Utils.toBytes(msg);
		if (position() != HEADER_LEN + mapInfoSize)
			throw new IllegalStateException("All info must be added before anything else");

		mapInfoSize += val.length+1;
		put(val);
		put((byte) 0);
	}

	public void addCopyright(Label cr) {
		copyrights.add(cr);
	}

	public void addPointOverview(Overview ov) {
		pointOverviews.add(ov);
	}

	public void addPolylineOverview(Overview ov) {
		polylineOverviews.add(ov);
	}

	/**
	 * Anything waiting to be written is dealt with here.
	 */
	private void prepare() {
		// Write out the map levels (zoom)
		mapLevelPos = position();
		for (int i = 15; i >= 0; i--) {
			// They need to be written in reverse order I think
			Zoom z = mapLevels[i];
			if (z == null)
				continue;
			mapLevelsSize += MAP_LEVEL_REC_SIZE;
			z.write(this);
		}

		subdivPos = position();
		int subdivnum = 1; // numbers start at one

		// First prepare to number them all
		for (int i = 15; i >= 0; i--) {
			Zoom z = mapLevels[i];
			if (z == null)
				continue;

			Iterator<Subdivision> it = z.subdivIterator();
			while (it.hasNext()) {
				Subdivision sd = it.next();
				sd.setNumber(subdivnum++);
			}
		}

		// Now we can write them all out.
		for (int i = 15; i >= 0; i--) {
			Zoom z = mapLevels[i];
			if (z == null)
				continue;

			Iterator<Subdivision> it = z.subdivIterator();
			while (it.hasNext()) {
				Subdivision sd = it.next();
				if (it.hasNext())
					sd.setLast(false);
				else
					sd.setLast(true);
				
				sd.write(this);
				if (i == 0)
					subdivSize += SUBDIV_REC_SIZE;
				else
					subdivSize += SUBDIV_REC_SIZE2;
			}
		}
		putInt(lastRgnPos);

		// Write out the pointers to the labels that hold the copyright strings
		copyrightPos = position();
		for (Label l : copyrights) {
			copyrightSize += COPYRIGHT_REC_SIZE;
			put3(l.getOffset());
		}

		// Point overview section
		pointPos = position();
		for (Overview ov : pointOverviews) {
			ov.write(this);
			pointSize += POINT_REC_LEN;
		}

		polylinePos = position();
		for (Overview ov : polylineOverviews) {
			ov.write(this);
			polylineSize += POLYLINE_REC_LEN;
		}
	}

	private void writeHeader() throws IOException {
		put3(area.getMaxLat());
		put3(area.getMaxLong());
		put3(area.getMinLat());
		put3(area.getMinLong());

		putInt(mapLevelPos);
		putInt(mapLevelsSize);

		putInt(subdivPos);
		putInt(subdivSize);

		putInt(copyrightPos);
		putInt(copyrightSize);
		putChar(COPYRIGHT_REC_SIZE);

		int dataPos = copyrightPos + copyrightSize;

		putInt(0);

		//poiDisplayFlags = 1; // XXX
		put(poiDisplayFlags);

		put3(0x19);
		putInt(0xd0401);

		putChar((char) 1);
		put((byte) 0);

		putInt(polylinePos);
		putInt(polylineSize);
		putChar(POLYLINE_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		putInt(dataPos);
		putInt(polygonSize);
		putChar(POLYGON_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		putInt(pointPos);
		putInt(pointSize);
		putChar(POINT_REC_LEN);

		putChar((char) 0);
		putChar((char) 0);

		// Map ID
		putInt(mapId);

		// A bunch of flag that regulate mainly unknown things.  Certain values
		// determine if the map name/date and block lists are protected
		// from modification in an unknown way.
		byte[] flags = new byte[] {
				0x41, (byte) 0xBF,  0x02, 0x3B, (byte) 0x95, (byte) 0xB3,
				0x12, (byte) 0xF2, 0x40, (byte) 0xBF,  0x66, (byte) 0xF2,
				0x40, (byte) 0xBF, 0x66, (byte) 0xF2
		};
		if (HEADER_LEN > 154) {
			position(0x9a);
			put(flags);
		}
		
		position(HEADER_LEN);
		put(Utils.toBytes("My OSM Map"));
	}

	public void setMapId(int id) {
		mapId = id;
	}

	public void setLastRgnPos(int lastRgnPos) {
		this.lastRgnPos = lastRgnPos;
	}

	public void setPoiDisplayFlags(byte poiDisplayFlags) {
		this.poiDisplayFlags = poiDisplayFlags;
	}
}
