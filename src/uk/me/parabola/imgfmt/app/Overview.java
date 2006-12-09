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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

/**
 * This is for polyline, polygon and point overviews.  A type of
 * object and the highest level at which it is found.
 *
 * @author Steve Ratcliffe
 */
public class Overview implements Writable {
	private byte type;
	private byte maxLevel;
	private byte subType;

	private int size;


	public Overview(int type, int maxLevel, int subType) {
		this.type = (byte) type;
		this.maxLevel = (byte) maxLevel;
		this.subType = (byte) subType;
		this.size = 3;
	}

	public Overview(int type, int maxLevel) {
		this.type = (byte) type;
		this.maxLevel = (byte) maxLevel;
		this.size = 2;
	}

	public void setType(byte type) {
		this.type = type;
	}

	public void setMaxLevel(byte maxLevel) {
		this.maxLevel = maxLevel;
	}

	public void setSubType(byte subType) {
		this.subType = subType;
	}


	public void write(ImgFile file) {
		file.put(type);
		file.put(maxLevel);
		if (size > 2)
			file.put(subType);
	}
}
