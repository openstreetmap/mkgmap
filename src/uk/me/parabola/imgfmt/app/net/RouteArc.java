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

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * An arc joins two nodes within a {@link RouteCenter}.  There are also
 * links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	private static final Logger log = Logger.getLogger(RouteArc.class);
	
	// Flags A
	public static final int NEW_DIRECTION = 0x80;
	public static final int DIRECTION_SIGN = 0x40;
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

	// The node that this arc goes to
	private RouteNode node;
	
	private byte flagA;
	private byte flagB;
	private byte localNet = -1;
	private char length; // not really known

	public RouteArc(RouteNode node) {
		this.node = node;
	}

	public void write(ImgFileWriter writer) {
		offset = writer.position();
		writer.put(flagA);
		writer.put(flagB);
		writer.put((byte) 0);

		if (localNet != -1)
			writer.put(localNet);
		writer.put((byte) (length & 0x3f));
		writer.put(initialHeading);
	}

	public void setNewDir() {
		flagA |= NEW_DIRECTION;
	}
	
	public void writeSecond(ImgFileWriter writer, RouteNode ourNode) {
		writer.position(offset + 1);
		char val = (char) (flagB << 8);
		val |= (node.getOffset() - ourNode.getOffset()) & 0x3fff;

		// We write this big endian
		log.debug("val is", Integer.toHexString((int)val));
		writer.put((byte) (val >> 8));
		writer.put((byte) val);
	}

	public void setDirection() {
		flagA |= DIRECTION_SIGN;
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
	}

	public void setLength(int len) {
		length = (char) len;
		// Set lots of flags as approriate...
	}
}
