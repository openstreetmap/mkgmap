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
 * An index into mdr11.
 * There is a primary ordering on the type and a secondary ordering on the
 * record number of the mdr11 record.  The type is not actually stored
 * in this section, you use mdr9 to divide up this section into groups of
 * types.
 *
 * This section contains the subtype of each point.
 *
 * @author Steve Ratcliffe
 */
public class Mdr10Record extends RecordBase implements Comparable<Mdr10Record> {
	private int subtype;
	private Mdr11Record mdr11ref;
	private int fullType; // TODO remove this, just for testing

	public int compareTo(Mdr10Record o) {
		if (mdr11ref.getRecordNumber() == o.mdr11ref.getRecordNumber())
			return 0;
		else if (mdr11ref.getRecordNumber() < o.mdr11ref.getRecordNumber())
			return -1;
		else
			return 1;
	}

	public Mdr11Record getMdr11ref() {
		return mdr11ref;
	}

	public void setMdr11ref(Mdr11Record mdr11ref) {
		this.mdr11ref = mdr11ref;
	}

	public int getSubtype() {
		return subtype;
	}

	public void setSubtype(int subtype) {
		this.subtype = subtype;
	}

	public void setFullType(int fullType) {
		this.fullType = fullType;
	}
}