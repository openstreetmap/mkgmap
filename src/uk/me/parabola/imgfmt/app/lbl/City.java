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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A city is in a region.  It also has (or can have anyway) a reference to
 * an indexed point within the map itself.
 * 
 * @author Steve Ratcliffe
 */
public class City {
	private static final int POINT_REF = 0x80;

	private final int index;

	private final Region region;

	// This determines if a label is being used or a subdivision and point
	// combo.
	private boolean pointRef;

	// The location of the city.  These could both be zero if we are using a
	// label instead.
	private char subdivision;
	private byte pointIndex;


	// You can have either a label or a subdivision and point.  This will be
	// null if the location is being specified.
	private Label label;

	public City(Region region, int index) {
		this.region = region;
		this.index = index;
	}

	void write(ImgFileWriter writer) {
		//writer.put3()
		if (pointRef) {
			writer.put(pointIndex);
			writer.putChar(subdivision);
		} else {
			writer.put3(label.getOffset());
		}
		
		char info = (char) (region.getIndex() & 0x3fff);
		if (pointRef)
			info |= POINT_REF;

		writer.putChar(info);
	}

	public int getIndex() {
		return index;
	}

	public void setLabel(Label label) {
		pointRef = false;
		this.label = label;
	}

	public void setPointIndex(byte pointIndex) {
		pointRef = true;
		this.pointIndex = pointIndex;
	}

	public void setSubdivision(char subdivision) {
		pointRef = true;
		this.subdivision = subdivision;
	}
}
