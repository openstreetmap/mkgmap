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
 * Create date: 07-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A zoom level (or map level) determines the amount of detail that
 * is shown as you zoom in and out.
 * Level 0 is the the most detailed level.
 *
 * A zoom level has a number of bits per co-ordinate and the number of
 * subdivisions at that level.
 *
 * The highest level must have one subdivision and have no elements I believe.
 *
 * @author Steve Ratcliffe
 */
public class Zoom {
	private int zoom;
	private boolean inherited;

	private int bitsPerCoord;

	private List<Subdivision> subdivs = new ArrayList<Subdivision>();


	/**
	 * Create a new zoom level.
	 *
	 * @param zoom The level between 0 and 15.
	 * @param bitsPerCoord The number of bits per coordinate, up to 24.
	 */
	public Zoom(int zoom, int bitsPerCoord) {
		this.zoom = zoom;
		this.bitsPerCoord = bitsPerCoord;
	}


	public int getZoom() {
		return zoom;
	}

	public int getNSubdivs() {
		return subdivs.size();
	}

	public Iterator<Subdivision> subdivIterator() {
		return subdivs.iterator();
	}

	public boolean isInherited() {
		return inherited;
	}

	public void setInherited(boolean inherited) {
		this.inherited = inherited;
	}

	public int getBitsPerCoord() {
		return bitsPerCoord;
	}

	public void write(ImgFile file) {
		file.put((byte) (zoom & 0x3 | (inherited ? 0x80 : 0)));
		file.put((byte) bitsPerCoord);
		file.putChar((char) subdivs.size());
	}

	public void setBitsPerCoord(int bitsPerCoord) {
		this.bitsPerCoord = bitsPerCoord;
	}

	void addSubdivision(Subdivision div) {
		subdivs.add(div);
	}
}
