/*
 * Copyright (C) 2008
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
 * Create date: 07-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * An arc joins two nodes within a {@link RouteCenter}.  This may be renamed
 * to a Segement.
 * The arc also references the road that it is a part of.
 *
 * There are also links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	private static final Logger log = Logger.getLogger(RouteArc.class);
	
	// Flags A
	public static final int NEW_DIRECTION = 0x80;
	public static final int FORWARD = 0x40;
	public static final int DESTINATION_CLASS_MASK = 0x7;
	public static final int CURVE = 0x20;
	public static final int EXTRA_LEN = 0x18;

	// Flags B
	public static final int LAST_LINK = 0x80;
	public static final int INTER_AREA = 0x40;

	private int offset;

	private byte initialHeading;
	private byte endDirection;

	private final RoadDef roadDef;

	// The nodes that this arc comes from and goes to
	private final RouteNode source;
	private final RouteNode dest;

	// The index in Table A describing this arc.
	private byte indexA;
	// The index in Table B that this arc goes via, if external.
	private byte indexB;
	
	private byte flagA;
	private byte flagB;

	private char length; // not really known

	/**
	 * Create a new arc.
	 *
	 * @param roadDef The road that this arc segment is part of.
	 * @param source The source node.
	 * @param dest The destination node.
	 * @param nextCoord The heading coordinate.
	 */
	public RouteArc(RoadDef roadDef, RouteNode source, RouteNode dest,
				Coord nextCoord) {
		this.roadDef = roadDef;
		this.source = source;
		this.dest = dest;

		this.length = calcDistance(nextCoord);
		log.debug("set length", (int)this.length);
		this.initialHeading = calcAngle(nextCoord);
		setDestinationClass(roadDef.getRoadClass()); // XXX: really?
	}

	public RouteNode getSource() {
		return source;
	}

	public RouteNode getDest() {
		return dest;
	}

	/**
	 * Provide an upper bound for the written size in bytes.
	 */
	public int boundSize() {
		return 6; // XXX: this could be reduced, and may increase
	}

	/**
	 * Is this an arc within the RouteCenter?
	 */
	public boolean isInternal() {
		// we might check that setInternal has been called before
		return (flagB & INTER_AREA) == 0;
	}

	public void setInternal(boolean internal) {
		if (internal)
			flagB &= ~INTER_AREA;
		else
			flagB |= INTER_AREA;
	}


	/**
	 * Set this arcs index into Table A.
	 */
	public void setIndexA(byte indexA) {
		this.indexA = indexA;
	}

	/**
	 * Set this arcs index into Table B. Applies to external arcs only.
	 */
	public void setIndexB(byte indexB) {
		assert !isInternal() : "Trying to set index on external arc.";
		this.indexB = indexB;
	}

	private byte calcAngle(Coord end) {
		Coord start = source.getCoord();

		log.debug("start", start.toDegreeString(), ", end", end.toDegreeString());

		// Quite possibly too slow...  TODO 
		double lat1 = Utils.toRadians(start.getLatitude());
		double lat2 = Utils.toRadians(end.getLatitude());
		double lon1 = Utils.toRadians(start.getLongitude());
		double lon2 = Utils.toRadians(end.getLongitude());

		//double dlat = lat2 - lat1;
		double dlon = lon2 - lon1;

		double y = Math.sin(dlon) * Math.cos(lat2);
		double x = Math.cos(lat1)*Math.sin(lat2) -
				Math.sin(lat1)*Math.cos(lat2)*Math.cos(dlon);
		double angle = Math.atan2(y, x);

		// angle is in radians
		log.debug("angle is ", angle, ", deg", angle*57.29);

		byte b = (byte) (256 * (angle / (2 * Math.PI)));
		log.debug("deg from ret val", (360 * b) / 256);

		return b;
	}

	private char calcDistance(Coord end) {
		Coord start = source.getCoord();

		double lat1 = Utils.toRadians(start.getLatitude());
		double lat2 = Utils.toRadians(end.getLatitude());
		double lon1 = Utils.toRadians(start.getLongitude());
		double lon2 = Utils.toRadians(end.getLongitude());

		double R = 6371000; // meters
		double d = Math.acos(Math.sin(lat1)*Math.sin(lat2) +
				Math.cos(lat1)*Math.cos(lat2) *
						Math.cos(lon2-lon1)) * R;
		log.debug("part length", d, ", feet", d * 3.28);
		return (char) (d * 3.28 / 4);
	}

	public void write(ImgFileWriter writer) {
		offset = writer.position();
		log.debug("writing arc at", offset, ", flagA=", Integer.toHexString(flagA));
		writer.put(flagA);

		if (isInternal()) {
			// space for 12 bit node offset, written in writeSecond.
			writer.put(flagB);
			writer.put((byte) 0);
		} else {
			writer.put((byte) (flagB | indexB));
		}

		writer.put(indexA);

		log.debug("wrting length", (length & 0x3f), ", complete", (int)length);
		writer.put((byte) (length & 0x3f));  // TODO more to do
		writer.put(initialHeading);
	}

	/**
	 * Second pass over the nodes in this RouteCenter.
	 * Node offsets are now all known, so we can write the pointers
	 * for internal arcs.
	 */
	public void writeSecond(ImgFileWriter writer) {
		if (!isInternal())
			return;

		writer.position(offset + 1);
		char val = (char) (flagB << 8);
		int diff = dest.getOffsetNod1() - source.getOffsetNod1();
		assert diff < 0x2000 && diff >= -0x2000
			: "relative pointer too large for 12 bits";
		val |= diff & 0x3fff;

		// We write this big endian
		log.debug("val is", Integer.toHexString((int)val));
		writer.put((byte) (val >> 8));
		writer.put((byte) val);
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public void setNewDir() {
		flagA |= NEW_DIRECTION;
	}

	public void setForward() {
		flagA |= FORWARD;
	}

	public boolean isForward() {
		return (flagA & FORWARD) != 0;
	}

	public boolean isReverse() {
		return !isForward();
	}

	public void setLast() {
		flagB |= LAST_LINK;
	}

	public void setDestinationClass(int destinationClass) {
		flagA |= (destinationClass & DESTINATION_CLASS_MASK);
	}

	public void setEndDirection(double ang) {
		endDirection = angleToByte(ang);
		flagA |= CURVE;
	}

	private static byte angleToByte(double ang) {
		return (byte) (255 * ang / 360);
	}
}
