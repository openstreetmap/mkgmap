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
public class Mdr5Record extends RecordBase implements Comparable<Mdr5Record> {
	private int cityIndex;
	private int lblOffset;
	private int stringOffset;
	private String name;


	public int compareTo(Mdr5Record o) {
		return name.compareTo(o.name);
	}

	public int getCityIndex() {
		return cityIndex;
	}

	public void setCityIndex(int cityIndex) {
		this.cityIndex = cityIndex;
	}

	public int getLblOffset() {
		return lblOffset;
	}

	public void setLblOffset(int lblOffset) {
		this.lblOffset = lblOffset;
	}

	public int getStringOffset() {
		return stringOffset;
	}

	public void setStringOffset(int stringOffset) {
		this.stringOffset = stringOffset;
	}

	public void setName(String name) {
		this.name = name;
	}
}
