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
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
	private final int level;
	private boolean inherited;

	private final int resolution;

	private final List<Subdivision> subdivs = new ArrayList<Subdivision>();

	/**
	 * Create a new zoom level.
	 *
	 * @param zoom The level between 0 and 15.
	 * @param resolution The number of bits per coordinate, up to 24.
	 */
	Zoom(int zoom, int resolution) {
		this.level = zoom;
		this.resolution = resolution;
	}


	public Iterator<Subdivision> subdivIterator() {
		return subdivs.iterator();
	}

	public void setInherited(boolean inherited) {
		this.inherited = inherited;
	}


	public int getLevel() {
		return level;
	}

	public int getResolution() {
		return resolution;
	}

	public int getShiftValue() {
		return 24 - resolution;
	}

	public void write(ImgFileWriter file) {
		file.put((byte) ((level & 0xf) | (inherited ? 0x80 : 0)));
		file.put((byte) resolution);
		file.putChar((char) subdivs.size());
	}

	public String toString() {
		return "L " + level + ':' + resolution;
	}
	
	void addSubdivision(Subdivision div) {
		subdivs.add(div);
	}
}
