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
public abstract class Overview {
	public static final int POINT_KIND = 1;
	public static final int LINE_KIND = 2;
	public static final int SHAPE_KIND = 3;

	private final int kind; // The kind of overview; point, line etc.
	private final byte type;
	private final byte subType;

	private byte maxLevel = 1; // XXX what to do with this?

	private final int size;

	protected Overview(int kind, int type, int subType) {
		this.kind = kind;
		this.type = (byte) type;
		this.subType = (byte) subType;
		if (kind == POINT_KIND)
			size = 3;
		else
			size = 2;
	}

	public void write(ImgFile file) {
		file.put(type);
		file.put(maxLevel);
		if (size > 2)
			file.put(subType);
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
}
