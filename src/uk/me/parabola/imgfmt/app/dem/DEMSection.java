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

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter;

public class DEMSection {
	private static final Logger log = Logger.getLogger(DEMSection.class);
	private static final int STD_DIM = 64; 
	private byte unknown1 = 0;
	private final int zoomLevel;
	private final boolean lastLevel;
	private final int pointsPerLat = STD_DIM;
	private final int pointsPerLon = STD_DIM;
	private final int nonStdHeight;
	private final int nonStdWidth;
	private final short flags1 = 0;
	private final int tilesLat;
	private final int tilesLon;
	private int offsetSize;
	private int baseSize;
	private int differenceSize;
	private boolean hasExtra;
	private int tileDescSize;
	private int dataOffset;
	private int dataOffset2;
	private final int pointsDistanceLat;
	private final int pointsDistanceLon;
	private final int top;
	private final int left;
	private int minHeight = Integer.MAX_VALUE;
	private int maxHeight = Integer.MIN_VALUE;
	private List<DEMTile> tiles = new ArrayList<>();
	
	public DEMSection(int zoomLevel, Area bbox, HGTConverter hgtConverter, int pointDist, boolean lastLevel) {
		this.zoomLevel = zoomLevel;
		this.lastLevel = lastLevel;
		
		if (pointDist == -1) {
			int res = (hgtConverter.getHighestRes() > 0) ? hgtConverter.getHighestRes() : 1200;
			pointDist = (int) ((1 << 29) / (res * 45));
		}
		this.top = bbox.getMaxLat() * 256;
		this.left = bbox.getMinLong() * 256;

		// calculate raster that starts at top left corner
		// last row and right column have non-standard height / row values 
		pointsDistanceLat = pointDist; 
		pointsDistanceLon = pointDist;
		
		int []latInfo = getTileInfo(bbox.getHeight() * 256, pointsDistanceLat);
		int []lonInfo = getTileInfo(bbox.getWidth() * 256, pointsDistanceLon);
		// store the values written to the header
		tilesLat = latInfo[0];
		tilesLon = lonInfo[0];
		nonStdHeight = latInfo[1];
		nonStdWidth = lonInfo[1];
		log.info("calculating zoom level:",zoomLevel,", dist:",pointDist,tilesLon,"x",tilesLat,"std tiles, nonstd x/y",nonStdWidth,"/",nonStdHeight);
		calcTiles(hgtConverter);
		hgtConverter = null;
	}

	/**
	 * Calculate the number of rows / columns and the non-standard height/width 
	 * @param demPoints number of 32 bit points 
	 * @param demDist distance between two sample points
	 * @return array with dimension and non standard value normalised to 1 .. 95
	 */
	private int[] getTileInfo(int demPoints, int demDist) {
		int resolution = STD_DIM * demDist;
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
		hgtConverter.setLatDist(pointsDistanceLat);
		hgtConverter.setLonDist(pointsDistanceLon);
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
				int bsLen = tile.getBitStreamLen();
				if (bsLen > 0) {
					if (tile.getBaseHeight() < minBaseHeight)
						minBaseHeight = tile.getBaseHeight();
					if (tile.getBaseHeight() > maxBaseHeight)
						maxBaseHeight = tile.getBaseHeight();
					if (tile.getMaxHeight() > maxHeight)
						maxHeight = tile.getMaxHeight();
					if (tile.getMaxDeltaHeight() > maxDeltaHeight)
						maxDeltaHeight = tile.getMaxDeltaHeight();
					dataLen += bsLen;
				}
			}
			if (lastLevel) {
				hgtConverter.freeMem();
			}
		}
		
		if (dataLen > 0) {
			minHeight = minBaseHeight;
		} else { 
			minHeight = 0;
			maxHeight = 0;
		}
		differenceSize = (maxDeltaHeight > 255) ? 2 : 1;
		if (-128 < minBaseHeight && maxBaseHeight < 128)
			baseSize = 1;
		else
			baseSize = 2;
		
		if (dataLen < 256)
			offsetSize = 1;
		else if (dataLen < 256 * 256)
			offsetSize = 2;
		else if (dataLen < 256 * 256 * 256)
			offsetSize = 3;
		else 
			offsetSize = 4;
		tileDescSize = offsetSize + baseSize + differenceSize + (hasExtra ? 1:0);
		
	}

	public void writeHeader(ImgFileWriter writer) {
		writer.put(unknown1);	//0x00 
		writer.put1(zoomLevel);	//0x01 
		writer.putInt(pointsPerLat);	//0x02
		writer.putInt(pointsPerLon);	//0x06 
		writer.putInt(nonStdHeight - 1);	//0x0A
		writer.putInt(nonStdWidth - 1);	//0x0E 
		writer.put2(flags1);	//0x12
		writer.putInt(tilesLon - 1);	//0x14
		writer.putInt(tilesLat - 1);	//0x18
		
		int recordDesc = offsetSize -1; // 0..3
		if (baseSize > 1)
			recordDesc |= (1 << 2);
		if (differenceSize > 1)
			recordDesc |= (1 << 3);
		if (hasExtra)
			recordDesc |=  (1 << 4); 
		writer.put2(recordDesc);	//0x1c
		writer.put2(tileDescSize);	//0x1e
		writer.putInt(dataOffset);	//0x20
		writer.putInt(dataOffset2);	//0x24
		writer.putInt(left);	//0x28 
		writer.putInt(top);	//0x2c 
		writer.putInt(pointsDistanceLat);	//0x30
		writer.putInt(pointsDistanceLon);	//0x34
		assert minHeight >= Short.MIN_VALUE && minHeight <= Short.MAX_VALUE; 
		writer.putChar((char) minHeight);	//0x38
		assert maxHeight >= Short.MIN_VALUE && maxHeight <= Short.MAX_VALUE; 
		writer.putChar((char) maxHeight);	//0x3a
	}

	public void writeRest(ImgFileWriter writer) {
		dataOffset = writer.position();

		int off = 0;
		for (DEMTile tile : tiles) {
			tile.setOffset(off);
			tile.writeHeader(writer, this);
			off += tile.getBitStreamLen();
		}
		dataOffset2 = writer.position();
		for (DEMTile tile : tiles) {
			tile.writeBitStreamData(writer);
		}
	}

	public int getOffsetSize() {
		return offsetSize;
	}

	public void setOffsetSize(byte offsetSize) {
		this.offsetSize = offsetSize;
	}

	public int getBaseSize() {
		return baseSize;
	}

	public void setBaseSize(byte baseSize) {
		this.baseSize = baseSize;
	}

	public int getDifferenceSize() {
		return differenceSize;
	}

	public void setDifferenceSize(byte differenceSize) {
		this.differenceSize = differenceSize;
	}

	public boolean hasExtra() {
		return hasExtra;
	}
}

