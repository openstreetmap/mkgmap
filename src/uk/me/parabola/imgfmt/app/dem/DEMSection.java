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

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.mkgmap.reader.hgt.HGTConverter;

public class DEMSection {
	private byte unknown1 = 0;
	private final int zoomLevel;
	private final int pointsPerLat = 64;
	private final int pointsPerLon = 64;
	private int nonStdHeight;
	private int nonStdWidth;
	private short flags1;
	private int tilesLat;
	private int tilesLon;
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
	
	public DEMSection(int zoomLevel, Area bbox, HGTConverter hgtConverter, int pointDist) {
		this.zoomLevel = zoomLevel;
		
		if (pointDist == -1) {
			int res = 1200;
			if (hgtConverter.getHighestRes() == 3600)
				res = 3600;
			this.pointsDistanceLat = (int) ((1 << 29) / (res * 45));
			this.pointsDistanceLon = (int) ((1 << 29) / (res * 45));
		} else {
			this.pointsDistanceLat = pointDist;
			this.pointsDistanceLon = pointDist;
		}
		
		this.top = bbox.getMaxLat() * 256;
		this.left = bbox.getMinLong() * 256;
		int bottom = bbox.getMinLat() * 256;
		int right= bbox.getMaxLong() * 256;
		int resLon = pointsPerLon * pointsDistanceLon;
		int resLat = pointsPerLat * pointsDistanceLat;
		
		this.tilesLat = Math.max((top - bottom) / resLat, 1);
		if (resLat * tilesLat < top - bottom) {
			tilesLat++;
		}
		this.tilesLon = Math.max((right - left) / resLon, 1);
		this.nonStdWidth = (right - (left + (tilesLon - 1) * resLon)) / pointsDistanceLon + 1;
		this.nonStdHeight = ((top - (tilesLat - 1) * resLat) - bottom) / pointsDistanceLat + 1;
		if (nonStdWidth >= 128) {
			tilesLon++;
			nonStdWidth -= pointsPerLon;
		}
		calcTiles(hgtConverter);
		hgtConverter = null;
	}

	private void calcTiles(HGTConverter hgtConverter) {
		int resLon = pointsPerLon * pointsDistanceLon;
		int resLat = pointsPerLat * pointsDistanceLat;
		int latOff;
		int lonOff;
		int dataLen = 0;
		int minBaseHeight = Integer.MAX_VALUE;
		int maxBaseHeight = Integer.MIN_VALUE;
		short[] noHeights = { hgtConverter.getOutsidePolyHeight() };
		java.awt.geom.Area polygon = hgtConverter.getPolygon();
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
				short[] realHeights = noHeights;
				int intersection = -1;
				java.awt.geom.Area testArea = null;
				if (polygon != null) {
					// create rectangle that slightly overlaps the tile 
					Rectangle2D r = new Rectangle2D.Double(lonOff / 256.0 - 0.01,
							(latOff - height * pointsDistanceLat) / 256.0 - 0.01,
							width * pointsDistanceLon / 256.0 + 0.02, 
							height * pointsDistanceLat / 256.0 + 0.02);
					testArea = new java.awt.geom.Area(r);
					testArea.intersect(polygon);
					if (testArea.isEmpty())
						intersection = 0;
					else if (testArea.getBounds().equals(r) && testArea.equals(new java.awt.geom.Area(r))) {
						intersection = 2;
						testArea = null;
					} else {
						intersection = 1;
					}
				}
				
				if (intersection != 0) {
					realHeights = new short[width * height];
					int count = 0;
					int py = latOff;
					for (int y = 0; y < height; y++) {
						int px = lonOff;
						for (int x = 0; x < width; x++) {
							realHeights[count++] = hgtConverter.getElevation(py, px, testArea); 
							// left to right
							px += pointsDistanceLon;
						}
						// top to bottom
						py -= pointsDistanceLat;
					}
				}
				DEMTile tile;
				tile = new DEMTile(this, n, m, width, height, realHeights);
				tiles.add(tile);
				int bsLen = tile.getBitStreamLen();
				if (bsLen > 0) {
					if (tile.getBaseHeight() < minBaseHeight)
						minBaseHeight = tile.getBaseHeight();
					if (tile.getBaseHeight() > maxBaseHeight)
						maxBaseHeight = tile.getBaseHeight();
					if (tile.getMaxHeight() > maxHeight)
						maxHeight = tile.getMaxHeight();
					dataLen += bsLen;
				}
			}
			if (m > 1 && tilesLon > 10 && pointsDistanceLat > 50000) {
				//TODO: this is a rather poor workaround to avoid an OutOfMemoryExeption 
				hgtConverter.freeMem();
			}
		}
		
		if (dataLen > 0) {
			minHeight = minBaseHeight;
		} else { 
			minHeight = 0;
			maxHeight = 0;
		}
		differenceSize = (maxHeight > 255) ? 2 : 1;
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
			tile.writeHeader(writer);
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

	public boolean isHasExtra() {
		return hasExtra;
	}

	public void setHasExtra(boolean hasExtra) {
		this.hasExtra = hasExtra;
	}
}

