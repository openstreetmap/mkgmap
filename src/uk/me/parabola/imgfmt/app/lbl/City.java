/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;

/**
 * A city is in a region.  It also has (or can have anyway) a reference to
 * an indexed point within the map itself.
 * 
 * @author Steve Ratcliffe
 */
public class City implements Comparable<City> {
	private static final int POINT_REF = 0x8000;
	private static final int REGION_IS_COUNTRY = 0x4000;

	private int index = -1;

	private final Region region;
	private final Country country;

	// This determines if a label is being used or a subdivision and point
	// combo.
	private boolean pointRef;

	// The location of the city.  These could both be zero if we are using a
	// label instead.
	private Subdivision subdivision;
	private byte pointIndex;


	// You can have either a label or a subdivision and point.  This will be
	// null if the location is being specified.
	private Label label;

	public City(Region region) {
		this.region = region;
		this.country = null;
	}

	public City(Country country) {
		this.country = country;
		this.region = null;
	}

	void write(ImgFileWriter writer) {
		//writer.put3()
		if (pointRef) {
		    //		    System.err.println("City point = " + (int)pointIndex + " div = " + subdivision.getNumber());
			writer.put(pointIndex);
			writer.putChar((char)subdivision.getNumber());
		} else {
			writer.put3(label.getOffset());
		}

		char info;
		if(region != null)
		    info = (char) (region.getIndex() & 0x3fff);
		else
		    info = (char) (REGION_IS_COUNTRY | (country.getIndex() & 0x3fff));
		if (pointRef)
			info |= POINT_REF;

		writer.putChar(info);
	}

	public int getIndex() {
		if (index == -1)
			throw new IllegalStateException("Offset not known yet.");
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public void setLabel(Label label) {
		pointRef = false;
		this.label = label;
	}

	public void setPointIndex(byte pointIndex) {
		pointRef = true;
		this.pointIndex = pointIndex;
	}

	public void setSubdivision(Subdivision subdivision) {
		pointRef = true;
		this.subdivision = subdivision;
	}

	public int compareTo(City other) {
		if(other == this)
			return 0;
		if(label != null && other.label != null)
			return label.compareTo(other.label);
		return hashCode() - other.hashCode();
	}

	public String toString() {
		String result = "";
		if(label != null)
			result += label.getText();
		if (subdivision != null)
			result += subdivision.getNumber() + "/" + pointIndex;
		if(country != null)
			result += " in country " + (0 + country.getIndex());
		if(region != null)
			result += " in region " + (0 + region.getIndex());

		return result;
	}

	public int getSubdivNumber() {
		return subdivision.getNumber();
	}

	public int getPointIndex() {
		return pointIndex;
	}

	public Label getLabel() {
		return label;
	}

	public int getRegionNumber() {
		if (region == null)
			return 0;
		else
			return region.getIndex();
	}
}
