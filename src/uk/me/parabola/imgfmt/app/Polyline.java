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
 * Create date: 11-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import java.util.BitSet;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a multi-segment line.  Eg for a road. As with all map objects
 * it can only exist as part of a subdivision.
 *
 * Writing these out is particularly tricky as deltas between points are packed
 * into the smallest number of bits possible.
 *
 * I am not trying to make the smallest map, so it may not be totally optimum.
 * 
 * @author Steve Ratcliffe
 */
public class Polyline extends MapObject {

	// Set it it is a one-way street for example.
	private boolean direction;

	private boolean extraBit;
	private boolean dataInNet;

	// If the bitstreamLength is two bytes or one.
	private boolean twoByteLen;
	private int bitstreamLength;

	private byte bitstreamInfo;

	// The actual points that make up the line.
	private List<Coord> points = new ArrayList<Coord>();

	private BitSet bitstream;

	public Polyline(Subdivision div, int type) {
		setSubdiv(div);
		setType(type);
	}

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFile file) {
		// Prepare the information that we need.
	}

	public void addCoord(Coord co) {
		points.add(co);
	}
}
