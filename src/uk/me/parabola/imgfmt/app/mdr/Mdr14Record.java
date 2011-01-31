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
 * A country record.
 * @author Steve Ratcliffe
 */
public class Mdr14Record extends RecordBase implements Comparable<Mdr14Record> {
	private int countryIndex;
	private int strOff;
	private String name;

	/**
	 * Sort by map id and then country id like for regions.  We don't have
	 * any evidence that this is necessary, but it would be surprising if
	 * it wasn't.
	 */
	public int compareTo(Mdr14Record o) {
		int v1 = (getMapIndex()<<16) + countryIndex;
		int v2 = (o.getMapIndex()<<16) + o.countryIndex;
		if (v1 < v2)
			return -1;
		else if (v1 > v2)
			return 1;
		else
			return 0;
	}

	public int getCountryIndex() {
		return countryIndex;
	}

	public void setCountryIndex(int countryIndex) {
		this.countryIndex = countryIndex;
	}

	public int getStrOff() {
		return strOff;
	}

	public void setStrOff(int strOff) {
		this.strOff = strOff;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
