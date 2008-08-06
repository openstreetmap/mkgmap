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
import java.util.Iterator;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * A routing node with its connections to other nodes via roads.
 *
 * @author Steve Ratcliffe
 */
public class RouteNode {
	private static final Logger log = Logger.getLogger(RouteNode.class);
	
	// Values for the first flag byte at offset 1
	private static final byte F_BOUNDRY = 0x08;
	private static final byte F_RESTRICTIONS = 0x10;
	private static final byte F_LARGE_OFFSETS = 0x20;
	private static final byte F_UNK_NEEDED = 0x44; // XXX

	private int offset = -1;

	@Deprecated
	private int nodeId; // XXX not needed at this point?

	private List<RouteArc> arcs = new ArrayList<RouteArc>();
	
	private byte flags = F_UNK_NEEDED;

	private char latOff;
	private char lonOff;

	@Deprecated
	private static int nodeCount;
	@Deprecated
	public RouteNode() {
		nodeId = nodeCount++;
	}

	public void addArc(RouteArc arc) {
		if (!arcs.isEmpty())
			arc.setNewDir();
		arcs.add(arc);
	}
	
	public void write(ImgFileWriter writer) {
		log.debug("writing node, first pass, nod1", nodeId);
		offset = writer.position();

		writer.put((byte) 0);  // will be overwritten later
		writer.put(flags);

		if ((flags & F_LARGE_OFFSETS) == F_LARGE_OFFSETS) {
			writer.putInt((lonOff << 16) | (latOff & 0xffff));
		} else {
			writer.put3((latOff << 12) | (lonOff & 0xfff));
		}

		if (!arcs.isEmpty()) {
			arcs.get(arcs.size() - 1).setLast();
			for (RouteArc arc : arcs)
				arc.write(writer);
		}
	}

	public int getOffset() {
		assert offset != -1: "failed for node " + nodeId;
		return offset;
	}

	public void setCoord(Coord centralPoint, Coord coord) {
		log.debug("center", centralPoint, ", coord", coord.toDegreeString());
		setLatOff(coord.getLatitude() - centralPoint.getLatitude());
		setLonOff(coord.getLongitude() - centralPoint.getLongitude());
	}

	private void setLatOff(int latOff) {
		if (latOff > 0xfff || latOff < -0xfff)
			flags |= F_LARGE_OFFSETS;

		log.debug("lat off", Integer.toHexString(latOff));
		this.latOff = (char) latOff;
	}

	private void setLonOff(int lonOff) {
		if (lonOff > 0xfff || lonOff < -0xfff)
			flags |= F_LARGE_OFFSETS;
		log.debug("long off", Integer.toHexString(lonOff));
		this.lonOff = (char) lonOff;

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
