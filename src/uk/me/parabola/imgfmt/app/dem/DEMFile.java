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

	/**
	 * Calculate the DEM data for a tile. 
	 * @param area the bounding box of the tile
	 * @param demPolygonMapUnits a bounding polygon which might be smaller than the area 
	 * @param pathToHGT comma separated list of directories or zip files
	 * @param pointDistances list of distances which determine the resolution
	 * @param outsidePolygonHeight the height value that should be used for points outside of the bounding polygon 
	 */
	public void calc(Area area, java.awt.geom.Area demPolygonMapUnits, String pathToHGT, List<Integer> pointDistances, short outsidePolygonHeight) {
		HGTConverter hgtConverter = new HGTConverter(pathToHGT, area, demPolygonMapUnits);
		hgtConverter.setOutsidePolygonHeight(outsidePolygonHeight);

		int zoom = 0;
		for (int pointDist : pointDistances) {
			DEMSection section = new DEMSection(zoom++, area, hgtConverter, pointDist);
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
}
