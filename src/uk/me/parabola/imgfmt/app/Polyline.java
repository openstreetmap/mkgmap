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
 * Represents a multi-segment line.  Eg for a road.
 * 
 * @author Steve Ratcliffe
 */
public class Polyline implements Writable {

	// All lines are in a division and many aspects of it are with respect to
	// the division.
	private Subdivision div;

	// The lable for this road
	private Label label;

	// The type of road etc.
	private int type;

	// Set it it is a one-way street for example.
	private boolean direction;

	private boolean extraBit;
	private boolean dataInNet;

	// These long and lat values are relative to the subdivision center.
	// Must be shifted depending on the zoom level.
	private int longitude;
	private int latitude;

	// If the bitstreamLength is two bytes or one.
	private boolean twoByteLen;
	private int bitstreamLength;

	private byte bitstreamInfo;

	// The actual points that make up the line.
	private List<Coord> points = new ArrayList<Coord>();

	private BitSet bitstream;

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFile file) {
		// Prepare the information that we need.
	}
}
