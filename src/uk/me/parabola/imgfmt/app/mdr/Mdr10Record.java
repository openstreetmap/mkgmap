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
 * @author Steve Ratcliffe
 */
public class Mdr10Record extends RecordBase implements Comparable<Mdr10Record> {
	private int type;
	private Mdr11Record mdr11ref;

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

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}
}