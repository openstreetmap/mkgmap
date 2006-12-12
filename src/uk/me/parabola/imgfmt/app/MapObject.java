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
 * Create date: 12-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

/**
 * An object that appears in a map.  One of point, polyline, polygon or indexed
 * point.
 *
 * All objects appear in a subdivision and are relative to it.  You cannot
 * know where the object is or its size without knowing the subdivision it
 * is in.
 *
 * @author Steve Ratcliffe
 */
public abstract class MapObject {
	// All lines are in a division and many aspects of it are with respect to
	// the division.
	private Subdivision subdiv;

	// The lable for this object
	private Label label;

	// The type of road etc.
	private int type;

	// These long and lat values are relative to the subdivision center.
	// Must be shifted depending on the zoom level.
	private int longitude;
	private int latitude;

	/**
	 * Write this object to the given file.
	 *
	 * @param f The file to write to. It is usually the RGN file.
	 */
	public abstract void write(ImgFile f);

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

	public void setLatitude(int latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(int longitude) {
		this.longitude = longitude;
	}

	public Subdivision getSubdiv() {
		return subdiv;
	}

	public void setSubdiv(Subdivision subdiv) {
		this.subdiv = subdiv;
	}

	public Label getLabel() {
		return label;
	}
}
