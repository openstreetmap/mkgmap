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
 * Represents the mapping between the Garmin map levels and the built-in
 * resolutions.  The resolutions go from 1 to 24 and the levels start at 0 and
 * are defined by the map maker.  For each level you assign a resolution to it.
 * The resolution for each level must be lower than that of the level below.
 *
 * As an example you might have the following level=resolution pairs:
 * 0=24, 1=22, 2=20, 3=19.
 *
 * Note that level 0 is the most detailed level, whereas 24 is the most detailed
 * resolution.
 *
 * The highest numbered level must be empty and cover the whole map.
 *
 * @author Steve Ratcliffe
*/
public class LevelInfo {
	private final int level;
	private int bits;
	//private LevelFilter filter;

	public LevelInfo(int level, int bits) {
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

	public int getBits() {
		return bits;
	}

}
