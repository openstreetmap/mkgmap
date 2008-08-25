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

	//private boolean newDirection;
	//private boolean sign;

	//private byte destinationClass;

	private final RoadDef roadDef;

	// The node that this arc goes to
	private final RouteNode node;
	private RouteArc other;
	
	private byte flagA;
	private byte flagB;
	private byte localNet = -1;
	private char length; // not really known

	/**
	 * Create an arc of the given road towards the given node.  This will be
	 * added to the node that is the start point.
	 * We don't currently have the start node in here.
	 * @param roadDef The road that the origin point and the destination node
	 * are part of.
	 * @param node The destination node.
	 */
	public RouteArc(RoadDef roadDef, RouteNode node) {
		this.roadDef = roadDef;
		this.node = node;
	}

	/**
	 * Create a new arc.
	 *
	 * @param roadDef The road that this arc segment is part of.
	 *
	 * @param node2 The destination node.
	 * @param start The coordinate of the start node,
	 * @param nextCoord The heading coordinate.
	 */
	public RouteArc(RoadDef roadDef, RouteNode node2, Coord start, Coord nextCoord) {
		this.roadDef = roadDef;
		this.node = node2;

		this.length = calcDistance(start, nextCoord);
		log.debug("set length", (int)this.length);
		this.initialHeading = calcAngle(start, nextCoord);
	}

	private byte calcAngle(Coord start, Coord end) {
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


	private char calcDistance(Coord start, Coord end) {
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
		writer.put(flagB);
		writer.put((byte) 0);

		if (localNet != -1)
			writer.put(localNet);
		log.debug("wrting length", (length & 0x3f), ", complete", (int)length);
		writer.put((byte) (length & 0x3f));  // TODO more to do
		writer.put(initialHeading);
	}

	public void setNewDir() {
		flagA |= NEW_DIRECTION;
	}
	
	public void writeSecond(ImgFileWriter writer, RouteNode ourNode) {
		writer.position(offset + 1);
		char val = (char) (flagB << 8);
		val |= (node.getOffsetNod1() - ourNode.getOffsetNod1()) & 0x3fff;

		// We write this big endian
		log.debug("val is", Integer.toHexString((int)val));
		writer.put((byte) (val >> 8));
		writer.put((byte) val);
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public void setOther(RouteArc other) {
		this.other = other;
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

	public void setDestinationClass(byte destinationClass) {
		flagA |= (destinationClass & DESTINATION_CLASS_MASK);
	}

	public void setHeading(double ang) {
		initialHeading = angleToByte(ang);
	}

	public void setEndDirection(double ang) {
		endDirection = angleToByte(ang);
		flagA |= CURVE;
	}

	private byte angleToByte(double ang) {
		return (byte) (255 * ang / 360);
	}

	public void setLocalNet(int localNet) {
		this.localNet = (byte) localNet;
		if (other != null)
			other.localNet = (byte) localNet;
	}

	public void setLength(int len) {
		assert false;
		//length = (char) len;
		// Set lots of flags as approriate...
	}

	public void setHeading(Coord heading) {
		
	}
}
