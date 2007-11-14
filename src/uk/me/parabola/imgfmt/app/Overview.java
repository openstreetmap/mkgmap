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
 * This is for polyline, polygon and point overviews.  A simple record that
 * holds the type of an object and the highest level at which it is found.
 *
 * It kind of declares which objects will appear in the map and if they
 * are not included here they will not be shown.
 *
 * @author Steve Ratcliffe
 */
public abstract class Overview implements Comparable<Overview> {
	public static final int POINT_KIND = 1;
	public static final int LINE_KIND = 2;
	public static final int SHAPE_KIND = 3;

	private final int kind; // The kind of overview; point, line etc.
	private final char type;
	private final char subType;
	private final int minResolution;
	private final int size;

	private int maxLevel;

	protected Overview(int kind, int fullType, int minres) {
		this.kind = kind;

		this.type = (char) (fullType >> 8 & 0xff);
		this.subType = (char) (fullType & 0xff);
		this.minResolution = minres;

		if (kind == POINT_KIND)
			size = 3;
		else
			size = 2;
	}

	public void write(ImgFile file) {
		file.put((byte) (type & 0xff));
		file.put((byte) maxLevel);
		if (size > 2)
			file.put((byte) (subType & 0xff));
	}

	/**
	 * Returns a hash code value for the object.
	 *
	 * @return a hash code value for this object.
	 * @see Object#equals(Object)
	 */
	public int hashCode() {
		return (kind << 7) + (type << 3) + subType;
	}

	/**
	 * Indicates whether some other object is "equal to" this one.
	 *
	 * @param obj the reference object with which to compare.
	 * @return <code>true</code> if this object is the same as the obj
	 *         argument; <code>false</code> otherwise.
	 * @see #hashCode()
	 */
	public boolean equals(Object obj) {
		if (!(obj instanceof Overview))
			return false;

		Overview ov = (Overview) obj;
		if (ov.kind == kind && ov.type == type && ov.subType == subType)
			return true;

		return false;
	}

	public int getKind() {
		return kind;
	}

	/**
	 * Compares this object with the specified object for order.  Returns a
	 * negative integer, zero, or a positive integer as this object is less
	 * than, equal to, or greater than the specified object.
	 *
	 * @param ov the object to be compared.
	 * @return a negative integer, zero, or a positive integer as this object
	 *         is less than, equal to, or greater than the specified object.
	 * @throws ClassCastException if the specified object's type prevents it
	 *                            from being compared to this object.
	 */
	public int compareTo(Overview ov) {
		if (kind != ov.kind) {
			return kind > ov.kind ? 1 : -1;
		}

		int res;
		if (type == ov.type) {
			if (subType == ov.subType)
				res = 0;
			else if (subType > ov.subType)
				res = 1;
			else
				res = -1;
		} else {
			if (type == ov.type)
				res = 0;
			else if (type > ov.type)
				res = 1;
			else
				res = -1;
		}
		return res;
	}

	public void setMaxLevel(int maxLevel) {
		this.maxLevel = maxLevel;
	}

	public int getMinResolution() {
		return minResolution;
	}
}
