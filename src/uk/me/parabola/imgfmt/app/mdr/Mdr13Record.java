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
 * Information about a region.
 * @author Steve Ratcliffe
 */
public class Mdr13Record extends RecordBase implements Comparable<Mdr13Record> {
	private int regionIndex;
	private int countryIndex;
	private int lblOffset;
	private int strOffset;
	private String name;
	private Mdr14Record mdr14;

	/**
	 * We sort first by map id and then by region id.
	 */
	public int compareTo(Mdr13Record o) {
		int v1 = (getMapIndex()<<16) + regionIndex;
		int v2 = (o.getMapIndex()<<16) + o.regionIndex;
		if (v1 < v2)
			return -1;
		else if (v1 > v2)
			return 1;
		else
			return 0;
	}

	public int getRegionIndex() {
		return regionIndex;
	}

	public void setRegionIndex(int regionIndex) {
		this.regionIndex = regionIndex;
	}

	public int getStrOffset() {
		return strOffset;
	}

	public void setStrOffset(int strOffset) {
		this.strOffset = strOffset;
	}

	public void setCountryIndex(int countryIndex) {
		this.countryIndex = countryIndex;
	}

	public int getCountryIndex() {
		return countryIndex;
	}

	public int getLblOffset() {
		return lblOffset;
	}

	public void setLblOffset(int lblOffset) {
		this.lblOffset = lblOffset;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Mdr14Record getMdr14() {
		return mdr14;
	}

	public void setMdr14(Mdr14Record mdr14) {
		this.mdr14 = mdr14;
	}
}
