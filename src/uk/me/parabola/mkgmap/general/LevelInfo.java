/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 02-Sep-2007
 */
package uk.me.parabola.mkgmap.general;

/**
 * @author Steve Ratcliffe
*/
public class LevelInfo {
	private int level;
	private int bits;
	//private LevelFilter filter;

	LevelInfo(int level, int bits) {
		this.level = level;
		this.bits = bits;
	}

	/**
	 * Returns a string representation of the object. In general, the
	 * <code>toString</code> method returns a string that
	 * "textually represents" this object.
	 *
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "L" + level + " B" + bits;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public int getBits() {
		return bits;
	}

	public void setBits(int bits) {
		this.bits = bits;
	}
}
