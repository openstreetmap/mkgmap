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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

public class DEMSection {
	private byte unknown1;
	private final int zoomLevel;
	private int pointsPerLat;
	private int pointsPerLon;
	private int nonStdHeight;
	private int nonStdWidth;
	private short unknown2;
	private int tilesLat;
	private int tilesLon;
	private byte offsetSize;
	private byte baseSize;
	private byte differenceSize;
	private boolean hasExtra;
	private short tileDescSize;
	private int dataOffset;
	private int dataOffset2;
	private int pointsDistanceLat;
	private int pointsDistanceLon;
	private int top;
	private int left;
	private char minHeight;
	private char maxHeight;
	List<DEMTile> tiles = new ArrayList<>();
	
	
	public DEMSection(int zoomLevel) {
		this.zoomLevel = zoomLevel;
		calcData();
	}

	public void writeHeader(ImgFileWriter writer) {
		writeSectorHeader(writer);
		
		for (DEMTile tile : tiles) {
			tile.writeHeader(writer);
		}
	}

	private void writeSectorHeader(ImgFileWriter writer) {
		writer.put(unknown1);	//0x00 
		writer.put1(zoomLevel);	//0x01 
		writer.putInt(pointsPerLat);	//0x02
		writer.putInt(pointsPerLon);	//0x06 
		writer.putInt(nonStdHeight - 1);	//0x0A
		writer.putInt(nonStdWidth - 1);	//0x0E 
		writer.put2(unknown2);	//0x12
		writer.putInt(tilesLon - 1);	//0x14
		writer.putInt(tilesLat - 1);	//0x18
		int recordDesc = 0;	//TODO : calculate 
		writer.put2(recordDesc);	//0x1c
		writer.put2(tileDescSize);	//0x1e
		writer.putInt(dataOffset);	//0x20 // TODO unsigned ?  
		writer.putInt(dataOffset2);	//0x24 // TODO unsigned ?  
		writer.putInt(left);	//0x28 
		writer.putInt(top);	//0x2c 
		writer.putInt(pointsDistanceLat);	//0x30
		writer.putInt(pointsDistanceLon);	//0x34
		writer.putChar(minHeight);	//0x38
		writer.putChar(maxHeight);	//0x3a
	}

	public void writeRest(ImgFileWriter writer) {
		for (DEMTile tile : tiles) {
			tile.writeBitStreamData(writer);
		}
	}

	public byte getOffsetSize() {
		return offsetSize;
	}

	public void setOffsetSize(byte offsetSize) {
		this.offsetSize = offsetSize;
	}

	public byte getBaseSize() {
		return baseSize;
	}

	public void setBaseSize(byte baseSize) {
		this.baseSize = baseSize;
	}

	public byte getDifferenceSize() {
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
	
	public void calcData() {
		int[] realHeights = new int[64*64];
		realHeights[63*64] = 3; // sample from pdf
		DEMTile tile = new DEMTile(this, 0, 0, 64, 64, realHeights);
		return;
	}
	
	public static void main(String[] args) {
		DEMSection section = new DEMSection(0);
	}
	
	
}

