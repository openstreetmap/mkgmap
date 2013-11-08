/*
 * Copyright (C) 2006 - 2012.
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

package uk.me.parabola.imgfmt.app.trergn;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.log.Logger;

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

	// flags in the label offset
	private static final int FLAG_NETINFO = 0x800000;
	private static final int FLAG_EXTRABIT = 0x400000;

	// flags in the type
	private static final int FLAG_DIR = 0x40;
	private static final int FLAG_2BYTE_LEN = 0x80;

	// Reference to NET section, if any
	private RoadDef roaddef;

	// If a road gets subdivided into several segments, this
	// says whether this line is the last segment. Need this
	// for writing extra bits.
	private boolean lastSegment = true;

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

		// Prepare for writing by doing all the required calculations.

		LinePreparer w;
		try {
			// Prepare the information that we need.
			w = new LinePreparer(this);
		}
		catch (AssertionError ae) {
			log.error("Problem writing line (" + getClass() + ") of type 0x" + Integer.toHexString(getType()) + " containing " + points.size() + " points and starting at " + points.get(0).toOSMURL());
			log.error("  Subdivision shift is " + getSubdiv().getShift() +
					  " and its centre is at " + new Coord(getSubdiv().getLatitude(), getSubdiv().getLongitude()).toOSMURL());
			log.error("  " + ae.getMessage());
			if(roaddef != null)
				log.error("  Way is " + roaddef);
			return;
		}

		int minPointsRequired = (this instanceof Polygon)? 3 : 2;
		BitWriter bw = w.makeBitStream(minPointsRequired);
		if(bw == null) {
			log.error("Level " + getSubdiv().getZoom().getLevel() + " " + ((this instanceof Polygon)? "polygon" : "polyline") + " has less than " + minPointsRequired + " points, discarding");
			return;
		}

		// The type of feature, also contains a couple of flags hidden inside.
		byte b1 = (byte) getType();
		if (direction)
			b1 |= FLAG_DIR;  // Polylines only.

		int blen = bw.getLength() - 1; // allow for the sizes
		assert blen > 0 : "zero length bitstream";
		assert blen < 0x10000 : "bitstream too long " + blen;
		if (blen >= 0x100)
			b1 |= FLAG_2BYTE_LEN;

		file.put(b1);

		// The label, contains a couple of flags within it.
		int loff = getLabel().getOffset();
		if (w.isExtraBit())
			loff |= FLAG_EXTRABIT;

		// If this is a road, then we need to save the offset of the label
		// so that we can change it to the index in the net section
		if (roaddef != null) {
			roaddef.addLabel(getLabel());
			roaddef.addOffsetTarget(file.position(),
					FLAG_NETINFO | (loff & FLAG_EXTRABIT));
			// also add ref label(s) if present
			List<Label> refLabels = getRefLabels();
			if(refLabels != null)
				for(Label rl : refLabels)
					roaddef.addLabel(rl);
		}

		file.put3(loff);

		// The delta of the longitude from the subdivision centre point
		// note that this has already been calculated.
		file.putChar((char) getDeltaLong());
		file.putChar((char) getDeltaLat());
		if(log.isDebugEnabled())
			log.debug("out center", getDeltaLat(), getDeltaLong());

		if (blen < 0x100)
			file.put((byte) (blen & 0xff));
		else
			file.putChar((char) (blen & 0xffff));

		file.put(bw.getBytes(), 0, blen+1);
	}

	/*
	 * write the polyline to an OutputStream - only use for outputting
	 * lines with extended (3 byte) types.
	 *
	 */
	public void write(OutputStream stream) throws IOException {
		assert hasExtendedType();
		int type = getType();
		int labelOff = getLabel().getOffset();
		byte[] extraBytes = getExtTypeExtraBytes();

		LinePreparer w;
		try {
			// need to prepare line info before outputing lat/lon
			w = new LinePreparer(this);
		}
		catch (AssertionError ae) {
			log.error("Problem writing line (" + getClass() + ") of type 0x" + Integer.toHexString(getType()) + " containing " + points.size() + " points and starting at " + points.get(0).toOSMURL());
			log.error("  Subdivision shift is " + getSubdiv().getShift() +
					  " and its centre is at " + new Coord(getSubdiv().getLatitude(), getSubdiv().getLongitude()).toOSMURL());
			log.error("  " + ae.getMessage());
			if(roaddef != null)
				log.error("  Way is " + roaddef);
			return;
		}
		int minPointsRequired = (this instanceof Polygon)? 3 : 2;
		BitWriter bw = w.makeBitStream(minPointsRequired);
		if(bw == null) {
			log.error("Level " + getSubdiv().getZoom().getLevel() + " " + ((this instanceof Polygon)? "polygon" : "polyline") + " has less than " + minPointsRequired + " points, discarding");
			return;
		}
		int blen = bw.getLength();
		assert blen > 1 : "zero length bitstream";
		assert blen < 0x10000 : "bitstream too long " + blen;

		if(labelOff != 0)
			type |= 0x20;		// has label
		if(extraBytes != null)
			type |= 0x80;		// has extra bytes
		stream.write(type >> 8);
		stream.write(type);

		int deltaLong = getDeltaLong();
		int deltaLat = getDeltaLat();
		stream.write(deltaLong);
		stream.write(deltaLong >> 8);
		stream.write(deltaLat);
		stream.write(deltaLat >> 8);

		if (blen >= 0x7f) {
			stream.write((blen << 2) | 2);
			stream.write((blen << 2) >> 8);
		}
		else {
			stream.write((blen << 1) | 1);
		}

		stream.write(bw.getBytes(), 0, blen);

		if(labelOff != 0) {
			stream.write(labelOff);
			stream.write(labelOff >> 8);
			stream.write(labelOff >> 16);
		}

		if(extraBytes != null)
			stream.write(extraBytes);
	}

	public void addCoord(Coord co) {
		points.add(co);
	}
	
	public void addCoords(List<Coord> coords) {
		points.addAll(coords);
	}

	public List<Coord> getPoints() {
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

	public void setLastSegment(boolean last) {
		lastSegment = last;
	}

	public boolean isLastSegment() {
		return lastSegment;
	}

	public void setRoadDef(RoadDef rd) {
		this.roaddef = rd;
	}

	public int getOffsetNet1() {
		if (!isRoad())
			return 0;
		return roaddef.getOffsetNet1();
	}

	public boolean sharesNodeWith(Polyline other) {
		for (Coord p1 : points) {
			if (p1.getId() != 0) {
				// point is a node, see if the other line contain the
				// same node
				for (Coord p2 : other.points)
					if (p1.getId() == p2.getId())
						return true;
			}
		}

		return false;
	}

	public int getLat() {
		return getSubdiv().getLatitude() + (getDeltaLat() << getSubdiv().getShift());
	}

	public int getLong() {
		return getSubdiv().getLongitude() + (getDeltaLong() << getSubdiv().getShift());
	}
}
