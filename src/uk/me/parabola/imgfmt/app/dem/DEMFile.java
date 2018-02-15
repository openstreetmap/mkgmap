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
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter.InterpolationMethod;

/**
 * The DEM file. This consists of information about elevation. It is used for
 * hill shading and to calculation the "ele" values in gpx tracks. Based on work
 * of Frank Stinner.
 *
 * @author Gerd Petermann
 */
public class DEMFile extends ImgFile {
	private static final Logger log = Logger.getLogger(DEMFile.class);

	// allowed increase of DEM area
	public final static double EXTRA = 0.1d;
	protected final static double FACTOR = 45.0d / (1 << 29);

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

	/**
	 * Calculate the DEM data for a tile.
	 * 
	 * @param area
	 *            the bounding box of the tile
	 * @param demPolygonMapUnits
	 *            a bounding polygon which might be smaller than the area
	 * @param pathToHGT
	 *            comma separated list of directories or zip files
	 * @param pointDistances
	 *            list of distances which determine the resolution
	 * @param outsidePolygonHeight
	 *            the height value that should be used for points outside of the
	 *            bounding polygon
	 * @return a new bounding box that should be used for the TRE file
	 */
	public Area calc(Area area, java.awt.geom.Area demPolygonMapUnits, String pathToHGT, List<Integer> pointDistances,
			short outsidePolygonHeight, InterpolationMethod interpolationMethod) {
		// HGT area is extended by EXTRA degrees in each direction
		HGTConverter hgtConverter = new HGTConverter(pathToHGT, area, demPolygonMapUnits, EXTRA);
		hgtConverter.setInterpolationMethod(interpolationMethod);
		hgtConverter.setOutsidePolygonHeight(outsidePolygonHeight);
		
		log.info("orig bounds",area);

		int alignment = 4;
		Area treArea = calcTREBounds (area, alignment);
		log.info("TRE bounds",treArea);
		
		int top = treArea.getMaxLat() * 256;
		int bottom = treArea.getMinLat() * 256;
		int left = treArea.getMinLong() * 256;
		int right = treArea.getMaxLong() * 256;

		int zoom = 0;
		int lastDist = pointDistances.get(pointDistances.size() - 1);
		for (int pointDist : pointDistances) {
			int distance = pointDist;
			if (distance == -1) {
				int res = (hgtConverter.getHighestRes() > 0) ? hgtConverter.getHighestRes() : 1200;
				distance = (int) Math.round((1 << 29) / (res * 45.0D));
			}
			// last 4 bits of distance should be 0
			distance = ((distance + 8) / 16) * 16;

			int xTop = top;
			int xLeft = left;

			// align DEM to distance raster, if distance not bigger than
			// widening of HGT area
			if (distance < (int) Math.floor((EXTRA / 45.0D * (1 << 29)))) {
				xTop = moveUp(top, distance);
				xLeft = moveLeft(left, distance);
			}

			DEMSection section = new DEMSection(zoom++, xTop, xLeft, xTop - bottom, right - xLeft, hgtConverter,
					distance, pointDist == lastDist);
			demHeader.addSection(section);
		}
		return treArea;
	}

	private Area calcTREBounds(Area area, int alignment) {
		int treTop = area.getMaxLat() + 1;
		int treLeft = area.getMinLong() - 1;
		
		int treBottom = area.getMinLat() + 1;
		int treRight = area.getMaxLong() - 1;
		while (treTop % alignment != 0)
			++treTop;
		while (treLeft % alignment != 0)
			--treLeft;
		while (treBottom % alignment != 0)
			++treBottom;
		while (treRight % alignment != 0)
			--treRight;
		treBottom -= alignment;
		treRight += alignment;
		Area treArea = new Area(treBottom, treLeft, treTop, treRight);
		return treArea;
	}

	int moveUp(int origLat32, int distance) {
		int moved = origLat32;
		if (moved >= 0) {
			moved -= moved % distance;
			if (moved < 0x3FFFFFFF - distance)
				moved += distance;
		} else {
			moved -= moved % distance;
		}
		return moved;
	}

	int moveLeft(int origLon32, int distance) {
		int moved = origLon32;
		if (moved >= 0) {
			moved -= moved % distance;
		} else {
			moved -= moved % distance;
			if (moved > Integer.MIN_VALUE + distance)
				moved -= distance;
		}
		return moved;
	}

	public void write() {
		ImgFileWriter w = getWriter();
		if (w instanceof BufferedImgFileWriter) {
			// increase file size limit to 256MB, no idea what the limit is
			((BufferedImgFileWriter) w).setMaxSize(0xfffffff); 
		}
		getHeader().writeHeader(getWriter());
	}
}
