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
 * The details required to write a POI record to mdr 11.
 * @author Steve Ratcliffe
 */
public class Mdr11Record extends RecordBase implements Comparable<Mdr11Record> {
	private int pointIndex;
	private int subdiv;
	private int lblOffset;
	private int strOffset;
	private int recordNumber;
	private String name;
	private Mdr5Record city;

	public int compareTo(Mdr11Record o) {
		return name.compareTo(o.name);
	}

	public int getPointIndex() {
		return pointIndex;
	}

	public void setPointIndex(int pointIndex) {
		this.pointIndex = pointIndex;
	}

	public int getSubdiv() {
		return subdiv;
	}

	public void setSubdiv(int subdiv) {
		this.subdiv = subdiv;
	}

	public int getLblOffset() {
		return lblOffset;
	}

	public void setLblOffset(int lblOffset) {
		this.lblOffset = lblOffset;
	}

	public int getCityIndex() {
		return city == null ? 0 : city.getGlobalCityIndex();
	}

	public int getStrOffset() {
		return strOffset;
	}

	public void setStrOffset(int strOffset) {
		this.strOffset = strOffset;
	}

	public int getRecordNumber() {
		return recordNumber;
	}

	public void setRecordNumber(int recordNumber) {
		this.recordNumber = recordNumber;
	}

	public String getName() {
		assert name!=null;
		return name;
	}

	public void setName(String name) {
		assert name!=null;
		this.name = name;
	}

	public void setCity(Mdr5Record city) {
		this.city = city;
	}
}