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
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.log.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a multi-segment line.  Eg for a road. As with all map objects
 * it can only exist as part of a subdivision.
 *
 * Writing these out is particularly tricky as deltas between points are packed
 * into the smallest number of bits possible.
 *
 * I am not trying to make the smallest map, so it will not be totally optimum.
 *
 * @author Steve Ratcliffe
 */
public class Polyline extends MapObject {
	private static final Logger log = Logger.getLogger(Polyline.class);

	private int number;

	// Reference to NET section, if any
	private RoadDef roaddef;

	// Set if it is a one-way street for example.
	private boolean direction;

	// The actual points that make up the line.
	private final List<Coord> points = new ArrayList<Coord>();

	public Polyline(Subdivision div) {
		setSubdiv(div);
	}

	/**
	 * Format and write the contents of the object to the given
	 * file.
	 *
	 * @param file A reference to the file that should be written to.
	 */
	public void write(ImgFileWriter file) {
		// If there is nothing to do, then do nothing.
		if (points.size() < 2) {
			log.debug("less than two points, not writing");
			return;
		}

		// Prepare for writing by doing all the required calculations.

		// Prepare the information that we need.
		LinePreparer w = new LinePreparer(this);
		BitWriter bw = w.makeBitStream();

		// The type of feature, also contains a couple of flags hidden inside.
		byte b1 = (byte) getType();
		if (direction)
			b1 |= 0x40;  // Polylines only.

		int blen = bw.getLength() - 1; // allow for the sizes
		assert blen > 0 : "zero length bitstream";
		assert blen < 0x10000 : "bitstream too long " + blen;
		if (blen >= 0x100)
			b1 |= 0x80;

		file.put(b1);

		// The label, contains a couple of flags within it.
		int loff = getLabel().getOffset();
		if (w.isExtraBit())
			loff |= 0x400000;

		// If this is a road, then we need to save the offset of the label
		// so that we can change it to the index in the net section
		if (roaddef != null) {
			roaddef.addLabel(getLabel());
			roaddef.addOffsetTarget(file, 0x800000 | (loff & 0x400000));
		}

		file.put3(loff);

		// The delta of the longitude from the subdivision centre point
		// note that this has already been calculated.
		file.putChar((char) getDeltaLong());
		file.putChar((char) getDeltaLat());
		log.debug("out center", getDeltaLat(), getDeltaLong());

		if (blen < 0x100)
			file.put((byte) (blen & 0xff));
		else
			file.putChar((char) (blen & 0xffff));

		file.put(bw.getBytes(), 0, blen+1);
	}

	public void addCoord(Coord co) {
		points.add(co);
	}

	List<Coord> getPoints() {
		return points;
	}

	public void setDirection(boolean direction) {
		this.direction = direction;
	}

	public boolean isRoad() {
		return roaddef != null;
	}

	public boolean roadHasInternalNodes() {
		return roaddef.hasInternalNodes();
	}

	public void setRoadDef(RoadDef rd) {
		this.roaddef = rd;
	}

	void setNumber(int n) {
		number = n;
	}

	public int getNumber() {
		return number;
	}
}
