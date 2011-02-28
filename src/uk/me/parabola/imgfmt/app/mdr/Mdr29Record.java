/*
 * Copyright (C) 2011.
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
 * Holds the name and pointers to all the other country related sections for
 * the given name.
 *
 * @author Steve Ratcliffe
 */
public class Mdr29Record extends RecordBase {
	private String name;
	private int mdr24;
	private int mdr22;
	private int mdr25;
	private int mdr26;
	private int strOffset;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getMdr24() {
		return mdr24;
	}

	public void setMdr24(int mdr24) {
		if (this.mdr24 == 0)
			this.mdr24 = mdr24;
	}

	public int getMdr22() {
		return mdr22;
	}

	public void setMdr22(int mdr22) {
		if (this.mdr22 == 0)
			this.mdr22 = mdr22;
	}

	public int getMdr25() {
		return mdr25;
	}

	public void setMdr25(int mdr25) {
		this.mdr25 = mdr25;
	}

	public int getMdr26() {
		return mdr26;
	}

	public void setMdr26(int mdr26) {
		if (this.mdr26 == 0)
			this.mdr26 = mdr26;
	}

	public int getStrOffset() {
		return strOffset;
	}

	public void setStrOffset(int strOffset) {
		this.strOffset = strOffset;
	}
}
