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
 * There is one of these for each region name (ie no repeats) there are indexes
 * to the first entry with the same name in various other sections.
 *
 * @author Steve Ratcliffe
 */
public class Mdr28Record extends ConfigBase {
	private int index;
	private String name;
	private int mdr21;
	private int mdr23;
	private int mdr27;
	private int strOffset;
	private Mdr14Record mdr14;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getMdr21() {
		return mdr21;
	}

	public void setMdr21(int mdr21) {
		this.mdr21 = mdr21;
	}

	public int getMdr23() {
		return mdr23;
	}

	public void setMdr23(int mdr23) {
		this.mdr23 = mdr23;
	}

	public int getMdr27() {
		return mdr27;
	}

	public void setMdr27(int mdr27) {
		this.mdr27 = mdr27;
	}

	public int getStrOffset() {
		return strOffset;
	}

	public void setStrOffset(int strOffset) {
		this.strOffset = strOffset;
	}

	public Mdr14Record getMdr14() {
		return mdr14;
	}

	public void setMdr14(Mdr14Record mdr14) {
		this.mdr14 = mdr14;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
