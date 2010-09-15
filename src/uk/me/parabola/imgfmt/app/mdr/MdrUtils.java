/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

/**
 * A bunch of static routines for use in creating the MDR file.
 */
public class MdrUtils {

	/**
	 * Get the group number for the poi.  This is the first byte of the records
	 * in mdr9.
	 *
	 * Not entirely sure about how this works yet.
	 * @param fullType The primary type of the object.
	 * @return The group number.  This is a number between 1 and 9 (and later
	 * perhaps higher numbers such as 0x40, so do not assume there are no
	 * gaps).
	 */
	public static int getGroupForPoi(int fullType) {
		// We group pois based on their type.  This may not be the final thoughts on this.
		int type = MdrUtils.getTypeFromFullType(fullType);
		int group = 0;
		if (fullType < 0xf)
			group = 1;
		else if (type >= 0x2a && type <= 0x30) {
			group = type - 0x28;
		} else if (type == 0x28) {
			group = 9;
		}
		return group;
	}

	public static boolean canBeIndexed(int fullType) {
		return getGroupForPoi(fullType) != 0;
	}

	private static int getTypeFromFullType(int fullType) {
		if ((fullType & 0xff00) > 0)
			return (fullType>>8) & 0xff;
		else
			return fullType & 0xff;
	}

	public static int getSubtypeFromFullType(int fullType) {
		return fullType & 0xff;
	}
}
