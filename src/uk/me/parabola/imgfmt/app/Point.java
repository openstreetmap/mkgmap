/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 09-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.Utils;

/**
 * Represents a particular point.
 * @author Steve Ratcliffe
 */
public class Point implements Writable {
	private int latitude;
	private int longitude;
	private Label label;
	private int type;
	private int subtype;

	public Point(int latitude, int longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public Point(Subdivision div, double latitude, double longitude) {
		// TODO These need to be shifted...
		this.latitude = div.getLatitude() - Utils.toMapUnit(latitude);
		this.longitude = div.getLongitude() - Utils.toMapUnit(longitude);
	}


	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFile file) {
		byte b = (byte) type;
		file.put(b);

		int off = label.getOffset();
		if (subtype != 0)
			off |= 0x800000;

		file.put3(off);
		file.putChar((char) longitude);
		file.putChar((char) latitude);
		if (subtype != 0)
			file.put((byte) subtype);
	}

	public int getLatitude() {
		return latitude;
	}

	public int getLongitude() {
		return longitude;
	}


	public void setLabel(Label label) {
		this.label = label;
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
}
