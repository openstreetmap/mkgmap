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

import java.util.Arrays;

import uk.me.parabola.imgfmt.ExitException;

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
public class LevelInfo implements Comparable<LevelInfo> {
	public static final String DEFAULT_LEVELS = "0:24, 1:22, 2:20, 3:18, 4:16";

	private final int level;
	private final int bits;

	// Set if this is a top level, use this when the format is supplying its own
	// top level.
	private boolean top;

	public LevelInfo(int level, int bits) {
		this.level = level;
		this.bits = bits;
	}

	/**
	 * Convert a string into an array of LevelInfo structures.
	 */
	public static LevelInfo[] createFromString(String levelSpec) {
		String[] desc = levelSpec.split("[, \\t\\n]+");
		LevelInfo[] levels = new LevelInfo[desc.length];

		int count = 0;
		for (String s : desc) {
			String[] keyVal = s.split("[=:]");
			if (keyVal == null || keyVal.length < 2) {
				throw new ExitException("Error: incorrect level specification " + levelSpec);
			}

			try {
				int key = Integer.parseInt(keyVal[0]);
				if (key < 0 || key > 16)
					throw new ExitException("Error: Level value out of range 0-16: " + s + " in levels specification " + levelSpec);
				int value = Integer.parseInt(keyVal[1]);
				if (value < 0 || value > 24)
					throw new ExitException("Error: Resolution value out of range 0-24: " + s + " in levels specification " + levelSpec);
				levels[count] = new LevelInfo(key, value);
			} catch (NumberFormatException e) {
				throw new ExitException("Error: Levels specification not all numbers: " + levelSpec + " check " + s);
			}
			count++;
		}

		Arrays.sort(levels);

		// If there are more than 8 levels the map can cause the
		// garmin to crash.
		if (levels.length > 8)
			throw new ExitException("Too many levels, the maximum is 8");

		return levels;
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

	public boolean isTop() {
		return top;
	}

	public void setTop(boolean top) {
		this.top = top;
	}

	/**
	 * These things sort so that the highest level number is the lowest.  OK
	 * so its a bit wierd.
	 *
	 * @param other The LevelInfo to compare to.
	 * @return Zero if they are equal and 1 if the object is greater and -1
	 * otherwise.
	 */
	public int compareTo(LevelInfo other) {

		if (other.getLevel() == getLevel())
			return 0;

		if (other.getLevel() > getLevel())
			return 1;
		else
			return -1;
	}
}
