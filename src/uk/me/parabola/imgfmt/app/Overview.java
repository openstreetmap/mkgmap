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

	/**
	 * Compares this object with the specified object for order.  Returns a
	 * negative integer, zero, or a positive integer as this object is less
	 * than, equal to, or greater than the specified object.
	 * <p/>
	 * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) ==
	 * -sgn(y.compareTo(x))</tt> for all <tt>x</tt> and <tt>y</tt>.  (This
	 * implies that <tt>x.compareTo(y)</tt> must throw an exception iff
	 * <tt>y.compareTo(x)</tt> throws an exception.)
	 * <p/>
	 * <p>The implementor must also ensure that the relation is transitive:
	 * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
	 * <tt>x.compareTo(z)&gt;0</tt>.
	 * <p/>
	 * <p>Finally, the implementor must ensure that <tt>x.compareTo(y)==0</tt>
	 * implies that <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for
	 * all <tt>z</tt>.
	 * <p/>
	 * <p>It is strongly recommended, but <i>not</i> strictly required that
	 * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>.  Generally speaking, any
	 * class that implements the <tt>Comparable</tt> interface and violates
	 * this condition should clearly indicate this fact.  The recommended
	 * language is "Note: this class has a natural ordering that is
	 * inconsistent with equals."
	 * <p/>
	 * <p>In the foregoing description, the notation
	 * <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
	 * <i>signum</i> function, which is defined to return one of <tt>-1</tt>,
	 * <tt>0</tt>, or <tt>1</tt> according to whether the value of
	 * <i>expression</i> is negative, zero or positive.
	 *
	 * @param ov the object to be compared.
	 * @return a negative integer, zero, or a positive integer as this object
	 *         is less than, equal to, or greater than the specified object.
	 * @throws ClassCastException if the specified object's type prevents it
	 *                            from being compared to this object.
	 */
	public int compareTo(Overview ov) {
		if (type == ov.type) {
			return subType - ov.subType;
		} else {
			return type - ov.type;
		}
	}
}
