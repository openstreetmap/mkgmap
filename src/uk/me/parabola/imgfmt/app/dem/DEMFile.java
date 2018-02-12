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

import java.util.List;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter.InterpolationMethod;

/**
 * The DEM file. This consists of information about elevation. It is used for hill shading
 * and to calculation the "ele" values in gpx tracks. Based on work of Frank Stinner. 
 *
 * @author Gerd Petermann
 */
public class DEMFile extends ImgFile {
	// allowed increase of DEM area
	public final static double EXTRA = 0.01d; 
	
	private final DEMHeader demHeader = new DEMHeader();
	private boolean setExtra;

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

	/**
	 * Calculate the DEM data for a tile. 
	 * @param area the bounding box of the tile
	 * @param demPolygonMapUnits a bounding polygon which might be smaller than the area 
	 * @param pathToHGT comma separated list of directories or zip files
	 * @param pointDistances list of distances which determine the resolution
	 * @param outsidePolygonHeight the height value that should be used for points outside of the bounding polygon
	 */
	public void calc(Area area, java.awt.geom.Area demPolygonMapUnits, String pathToHGT, List<Integer> pointDistances, short outsidePolygonHeight, InterpolationMethod interpolationMethod) {
		// HGT area is extended by EXTRA degrees in each direction
		HGTConverter hgtConverter = new HGTConverter(pathToHGT, area, demPolygonMapUnits, EXTRA);
		hgtConverter.setInterpolationMethod(interpolationMethod);
		hgtConverter.setOutsidePolygonHeight(outsidePolygonHeight);

		int top = area.getMaxLat() * 256;
		int bottom = area.getMinLat() * 256;
		int left = area.getMinLong() * 256;
		int right = area.getMaxLong() * 256;

		int zoom = 0;
		int lastDist = pointDistances.get(pointDistances.size()-1); 
		for (int pointDist : pointDistances) {
			int distance = pointDist;
			if (distance == -1) {
				int res = (hgtConverter.getHighestRes() > 0) ? hgtConverter.getHighestRes() : 1200;
				distance = (int) Math.round((1 << 29) / (res * 45.0D));
			}
			// last 4 bits of distance should be 0
			distance = ((distance + 8)/16)*16;

			int xtop = top;
			int xleft = left;

			// align DEM to distance raster, if distance not bigger than widening of HGT area
			if (distance < (int)Math.floor((EXTRA/45.0D * (1 << 29)))) {
				if (xtop >= 0) {
					xtop -= xtop % distance;
					if (xtop < 0x3FFFFFFF - distance)
						xtop += distance;
				}
				else {
					xtop -= xtop % distance;
				}

				if (xleft >= 0) {
					xleft -= xleft % distance;
				}
				else {
					xleft -= xleft % distance;
					if (xleft > Integer.MIN_VALUE + distance)
						xleft -= distance;
				}
			}

			DEMSection section = new DEMSection(zoom++, xtop, xleft, xtop - bottom, right - xleft, hgtConverter,
					distance, pointDist == lastDist, setExtra);
			demHeader.addSection(section);
		}
		return;
	}

	public void write() {
		ImgFileWriter w = getWriter();
		if (w instanceof BufferedImgFileWriter) {
			((BufferedImgFileWriter) w).setMaxSize(0xfffffff); // increase file size limit to 256MB, no idea what the limit is
		}
		getHeader().writeHeader(getWriter());
	}
	
	/**
	 * Experimental code to find reason for crash in MapSource. TODO: remove 
	 */
	public void setExtra() {
		setExtra = true;
	}

}
