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
 * The records in MDR 4 are a list of poi types with an unknown byte.
 */
public class Mdr4Record extends RecordBase implements Comparable<Mdr4Record> {
	private int type;
	private int subtype;
	private int unknown;

	public int compareTo(Mdr4Record o) {
		int t1 = ((type<<8) + subtype) & 0xffff;
		int t2 = ((o.type<<8) + o.subtype) & 0xffff;
		if (t1 == t2)
			return 0;
		else if (t1 < t2)
			return -1;
		else
			return 1;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Mdr4Record that = (Mdr4Record) o;

		if (subtype != that.subtype) return false;
		if (type != that.type) return false;

		return true;
	}


	public int hashCode() {
		int result = type;
		result = 31 * result + subtype;
		return result;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public int getSubtype() {
		return subtype;
	}

	public void setSubtype(int subtype) {
		this.subtype = subtype;
	}

	public int getUnknown() {
		return unknown;
	}

	public void setUnknown(int unknown) {
		this.unknown = unknown;
	}
}