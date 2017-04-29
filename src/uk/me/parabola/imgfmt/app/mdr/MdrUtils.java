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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * A bunch of static routines for use in creating the MDR file.
 */
public class MdrUtils {

	public static final int STREET_INDEX_PREFIX_LEN = 4;
	public static final int POI_INDEX_PREFIX_LEN = 4;
	public static final int MAX_GROUP = 9;

	/**
	 * Get the group number for the poi.  This is the first byte of the records
	 * in mdr9.
	 *
	 * Not entirely sure about how this works yet.
	 * @param fullType The primary type of the object.
	 * @return The group number.  This is a number between 1 and 9 (and later
	 * perhaps higher numbers such as 0x40, so do not assume there are no
	 * gaps).
	 * Group / Filed under
	 * 1 Cities
	 * 2 Food & Drink
	 * 3 Lodging
	 * 4-5 Recreation / Entertainment / Attractions
	 * 6 Shopping
	 * 7 Auto Services
	 * 8 Community
	 * 9 ?
	 * 
	 */
	public static int getGroupForPoi(int fullType) {
		// We group pois based on their type.  This may not be the final thoughts on this.
		int type = getTypeFromFullType(fullType);
		int group = 0;
		if (fullType <= 0xf)
			group = 1;
		else if (type >= 0x2a && type <= 0x30) { 
			group = type - 0x28;
		} else if (type == 0x28) {
			group = 9;
//		} else if (type >= 0x64 && type <= 0x66) {
//			group = type - 0x24;
		}
		assert group >= 0 && group <= MAX_GROUP : "invalid group " + Integer.toHexString(group);
		return group;
	}

	public static boolean canBeIndexed(int fullType) {
		return getGroupForPoi(fullType) != 0;
	}

	public static int getTypeFromFullType(int fullType) {
		if ((fullType & 0xfff00) > 0)
			return (fullType>>8) & 0xfff;
		else
			return fullType & 0xff;
	}

	/**
	 * Gets the subtype 
	 * @param fullType The type in the so-called 'full' format.
	 * @return If there is a subtype, then it is returned, else 0.
	 */
	public static int getSubtypeFromFullType(int fullType) {
		return fullType < 0xff ? 0 : fullType & 0xff;
	}

	/**
	 * Sort records that are sorted by a name.  They appropriate sort order will be used.
	 * @param sort The sort to be applied.
	 * @param list The list to be sorted.
	 * @param <T> One of the Mdr?Record types that need to be sorted on a text field, eg street name.
	 * @return A list of sort keys in the sorted order.  The original object is retrieved from the key
	 * by calling getObject().
	 */
	public static <T extends NamedRecord> List<SortKey<T>> sortList(Sort sort, List<T> list) {
		List<SortKey<T>> toSort = new ArrayList<SortKey<T>>(list.size());
		for (T m : list) {
			SortKey<T> sortKey = sort.createSortKey(m, m.getName(), m.getMapIndex());
			toSort.add(sortKey);
		}
		Collections.sort(toSort);
		return toSort;
	}

	/**
	 * The 'natural' type is always a combination of the type and subtype with the type
	 * shifted 5 bits and the sub type in the low 5 bits.
	 *
	 * For various reasons, we use 'fullType' in which the type is shifted up a full byte
	 * or is in the lower byte.
	 *
	 * @param ftype The so-called full type of the object.
	 * @return The natural type as defined above.
	 */
	public static int fullTypeToNaturalType(int ftype) {
		int type = getTypeFromFullType(ftype);
		int sub = 0;
		if ((ftype & ~0xff) != 0)
			sub = ftype & 0x1f;

		return type << 5 | sub;
	}
}
