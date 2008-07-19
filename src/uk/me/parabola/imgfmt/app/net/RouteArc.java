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

/**
 * An arc joins two nodes within a {@link RouteCenter}.  There are also
 * links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	// Flags A
	public static final int NEW_DIRECTION = 0x80;
	public static final int DIRECTION_SIGN = 0x40;
	public static final int DESTINATION_CLASS_MASK = 0x7;

	// Flags B
	public static final int LAST_LINK = 0x80;
	public static final int INTER_AREA = 0x40;

	private byte initialHeading;
	private byte endHeading;

	//private boolean newDirection;
	//private boolean sign;

	//private byte destinationClass;

	// The node that this arc goes to
	private RouteNode node;
	
	private byte flagsA;
	private byte flagsB;

	public RouteArc(RouteNode node) {
		this.node = node;
	}

	public void write(ImgFileWriter writer) {
		writer.put(flagsA);
		writer.put(flagsB);
		writer.put((byte) 0);
		writer.put3(0);
	}

	public void setDirection(boolean d) {
		if (d) {
			flagsA |= DIRECTION_SIGN;
		}
		//else {
		//	flagsA &= ~DIRECTION_SIGN;
		//}
	}

	public void setLast() {
		flagsB |= LAST_LINK;
	}

	public void setDestinationClass(byte destinationClass) {
		flagsA |= (destinationClass & DESTINATION_CLASS_MASK);
	}
}
