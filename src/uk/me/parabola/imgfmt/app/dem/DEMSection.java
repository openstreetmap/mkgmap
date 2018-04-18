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

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter;

public class DEMSection {
	private static final Logger log = Logger.getLogger(DEMSection.class);
	private static final int STD_DIM = 64; 
	private int unknown1 = 0;
	private final int zoomLevel;
	private final boolean lastLevel;
	private final int pointsPerLat = STD_DIM;
	private final int pointsPerLon = STD_DIM;
	private final int nonStdHeight;
	private final int nonStdWidth;
	private final short flags1 = 0;
	private final int tilesLat;
	private final int tilesLon;
	private int recordDesc;
	private int tileDescSize;
	private int dataOffset;
	private int dataOffset2;
	private final int pointsDistanceLat;
	private final int pointsDistanceLon;
	private final int top;
	private final int left;
	private boolean hasExtra;
	private int minHeight = Integer.MAX_VALUE;
	private int maxHeight = Integer.MIN_VALUE;
	private List<DEMTile> tiles = new ArrayList<>();

	/**
	 * Calculate the DEM data for the given position and resolution.
	 * @param zoomLevel the zoom level
	 * @param areaTop latitude of upper left corner in DEM units
	 * @param areaLeft longitude of upper left corner in DEM units
	 * @param areaHeight height in DEM units
	 * @param areaWidth width in DEM units
	 * @param hgtConverter the hgt converter
	 * @param pointDist distance in DEM units between to height samples  
	 * @param lastLevel: set to true to signal that readers are no longer needed for further levels 
	 */
	public DEMSection(int zoomLevel, int areaTop, int areaLeft, int areaHeight, int areaWidth,
			HGTConverter hgtConverter, int pointDist, boolean lastLevel) {
		this.zoomLevel = zoomLevel;
		this.lastLevel = lastLevel;
		
		this.top = areaTop;
		this.left = areaLeft;

		// calculate raster that starts at top left corner
		// last row and right column have non-standard height / row values 
		pointsDistanceLat = pointDist; 
		pointsDistanceLon = pointDist;

		// allow automatic selection of interpolation method for each zoom level
		hgtConverter.startNewLevel(pointDist);

		int[] latInfo = getTileInfo(areaHeight, pointsDistanceLat);
		int[] lonInfo = getTileInfo(areaWidth, pointsDistanceLon);
		// store the values written to the header
		tilesLat = latInfo[0];
		tilesLon = lonInfo[0];
		nonStdHeight = latInfo[1];
		nonStdWidth = lonInfo[1];
		log.info("calculating zoom level:",zoomLevel,", dist:",pointDist,tilesLon,"x",tilesLat,"std tiles, nonstd x/y",nonStdWidth,"/",nonStdHeight);
		calcTiles(hgtConverter);
	}

	/**
	 * Calculate the number of rows / columns and the non-standard height/width 
	 * @param demPoints number of 32 bit points 
	 * @param demDist distance between two sample points
	 * @return array with dimension and non standard value normalised to 1 .. 95
	 */
	private int[] getTileInfo(int demPoints, int demDist) {
		int resolution = STD_DIM * demDist;
		demPoints += demDist; // probably not needed, but Garmin seems to prefer large overlaps
		int nFull = demPoints / resolution;
		int rest = demPoints - nFull * resolution;
		int num = nFull;
		int nonstd = rest / demDist;
		if (rest % demDist != 0)
			++nonstd;
		// normalise non std value so that it is between 1 .. 95 because Garmin does it also
		if (nonstd >= STD_DIM / 2) {
			++num;
		} else {
			if (num > 0)
				nonstd += STD_DIM;
		}
		if (num == 0)
			num = 1;
		int[] res = {num, nonstd};
		return res;
	}
	
	
	private void calcTiles(HGTConverter hgtConverter) {
		int resLon = pointsPerLon * pointsDistanceLon;
		int resLat = pointsPerLat * pointsDistanceLat;
		int latOff;
		int lonOff;
		int dataLen = 0;
		int minBaseHeight = Integer.MAX_VALUE;
		int maxBaseHeight = Integer.MIN_VALUE;
		int maxDeltaHeight = Integer.MIN_VALUE;
	
		for (int m = 0; m < tilesLat; m++) {
			latOff = top - m * resLat;
			
			int height = pointsPerLat;
			if (m + 1 == tilesLat) {
				height = nonStdHeight;
			}
			for (int n = 0; n < tilesLon; n++) {
				lonOff = left + n * resLon;
				int width = pointsPerLon;
				if (n + 1 == tilesLon) {
					width = nonStdWidth;
				}
				short[] realHeights = hgtConverter.getHeights(latOff, lonOff, height, width);
				DEMTile tile;
				tile = new DEMTile(n, m, width, height, realHeights);
				tiles.add(tile);
				if (tile.getEncodingType() != 0)
					hasExtra = true;
				if (tile.hasValidHeights()) {
					if (tile.getBaseHeight() < minBaseHeight)
						minBaseHeight = tile.getBaseHeight();
					if (tile.getBaseHeight() > maxBaseHeight)
						maxBaseHeight = tile.getBaseHeight();
					if (tile.getMaxHeight() > maxHeight)
						maxHeight = tile.getMaxHeight();
					if (tile.getMaxDeltaHeight() > maxDeltaHeight)
						maxDeltaHeight = tile.getMaxDeltaHeight();
				}
				dataLen += tile.getBitStreamLen();
			}
			if (lastLevel) {
				hgtConverter.freeMem();
			}
		}
		
		hgtConverter.printStat();

		if (dataLen > 0) {
			minHeight = minBaseHeight;
		} else { 
			minHeight = 0;
			maxHeight = 0;
		}
		int deltaSize = (maxDeltaHeight <= 255) ? 1 : 2;
		int baseSize = (-128 < minBaseHeight && maxBaseHeight < 128) ? 1 : 2;
		int offsetSize = Utils.numberToPointerSize(dataLen);
		
		tileDescSize = offsetSize + baseSize + deltaSize + (hasExtra ? 1:0);
		recordDesc = offsetSize -1; // 0..3
		if (baseSize > 1)
			recordDesc |= (1 << 2);
		if (deltaSize > 1)
			recordDesc |= (1 << 3);
		if (hasExtra)
			recordDesc |=  (1 << 4); 
		
	}

	public void writeHeader(ImgFileWriter writer) {
		writer.put1u(unknown1);	//0x00 
		writer.put1u(zoomLevel);	//0x01 
		writer.put4(pointsPerLat);	//0x02
		writer.put4(pointsPerLon);	//0x06 
		writer.put4(nonStdHeight - 1);	//0x0A
		writer.put4(nonStdWidth - 1);	//0x0E 
		writer.put2u(flags1);	//0x12
		writer.put4(tilesLon - 1);	//0x14
		writer.put4(tilesLat - 1);	//0x18
		
		writer.put2u(recordDesc);	//0x1c
		writer.put2u(tileDescSize);	//0x1e
		writer.put4(dataOffset);	//0x20
		writer.put4(dataOffset2);	//0x24
		writer.put4(left);	//0x28 
		writer.put4(top);	//0x2c 
		writer.put4(pointsDistanceLat);	//0x30
		writer.put4(pointsDistanceLon);	//0x34
		writer.put2s(minHeight);	//0x38
		writer.put2s(maxHeight);	//0x3a
	}

	public void writeRest(ImgFileWriter writer) {
		dataOffset = writer.position();

		int off = 0;
		for (DEMTile tile : tiles) {
			tile.setOffset(off);
			tile.writeHeader(writer, recordDesc);
			off += tile.getBitStreamLen();
		}
		dataOffset2 = writer.position();
		for (DEMTile tile : tiles) {
			tile.writeBitStreamData(writer);
		}
	}
}

