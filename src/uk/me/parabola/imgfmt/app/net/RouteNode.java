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

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A routing node with its connections to other nodes via roads.
 *
 * @author Steve Ratcliffe
 */
public class RouteNode {
	// Values for the first flag byte at offset 1
	private static final byte F_BOUNDRY = 0x08;
	private static final byte F_RESTRICTIONS = 0x10;
	private static final byte F_LARGE_OFFSETS = 0x20;
	private static final byte F_UNK_NEEDED = 0x44; // XXX

	private int offset;
	private int nodeId; // XXX not needed at this point?

	private List<RouteArc> arcs = new ArrayList<RouteArc>();
	
	private byte flags = F_UNK_NEEDED;

	private char latOff;
	private char lonOff;

	public void addArc(RouteArc arc) {
		arcs.add(arc);
	}
	
	public void write(ImgFileWriter writer) {
		offset = writer.position();

		int latLonOff = calcLatLonOffsets();
		
		writer.put((byte) 0);  // will be overwritten later
		writer.put(flags);

		if ((flags & F_LARGE_OFFSETS) == 0)
			writer.put3(latLonOff);
		else
			writer.putInt(latLonOff);

		for (RouteArc arc : arcs)
			arc.write(writer);

	}

	private int calcLatLonOffsets() {
		if (latOff > 0xfff || lonOff > 0xfff)
			return (lonOff << 16) | (latOff & 0xffff);
		else
			return (lonOff << 12) | (latOff & 0xfff);
	}

	public int getOffset() {
		return offset;
	}

	public void setLatOff(char latOff) {
		this.latOff = latOff;
		if (latOff > 0xfff)
			flags |= F_LARGE_OFFSETS;
	}

	public void setLonOff(char lonOff) {
		this.lonOff = lonOff;
		if (lonOff > 0xfff)
			flags |= F_LARGE_OFFSETS;
	}

	public void writeSecond(ImgFileWriter writer) {
		for (RouteArc arc : arcs)
			arc.writeSecond(writer, this);
	}


	public Iterable<? extends RouteArc> arcsIteration() {
		return new Iterable<RouteArc>() {
			public Iterator<RouteArc> iterator() {
				return arcs.iterator();
			}
		};
	}
}
