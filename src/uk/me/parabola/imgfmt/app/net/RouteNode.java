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
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * A routing node with its connections to other nodes via roads.
 *
 * @author Steve Ratcliffe
 */
public class RouteNode {
	private static final Logger log = Logger.getLogger(RouteNode.class);

	/*
	 * 1. instantiate
	 * 2. setCoord, addArc
	 *      arcs, coords set
	 * 3. write
	 *      node offsets set in all nodes
	 * 4. writeSecond
	 */
	
	// Values for the first flag byte at offset 1
	private static final int F_BOUNDARY = 0x08;
	private static final int F_RESTRICTIONS = 0x10;
	private static final int F_LARGE_OFFSETS = 0x20;
	private static final int F_UNK_NEEDED = 0x44; // XXX

	private int offsetNod1 = -1;

	private RouteCenter routeCenter;

	@Deprecated
	private final int nodeId; // XXX not needed at this point?

	private final List<RouteArc> arcs = new ArrayList<RouteArc>();
	
	private int flags = F_UNK_NEEDED;

	private CoordNode coord;
	private char latOff;
	private char lonOff;

	@Deprecated
	private static int nodeCount;

	@Deprecated
	public RouteNode(Coord coord) {
		this.coord = (CoordNode) coord;
		nodeId = nodeCount++; // XXX: take coord.getId() instead?
		setBoundary(this.coord.isBoundary());
	}

	private boolean haveLargeOffsets() {
		return (flags & F_LARGE_OFFSETS) != 0;
	}

	private boolean haveRestrictions() {
		return (flags & F_RESTRICTIONS) != 0;
	}

	public void setBoundary(boolean b) {
		if (b)
			flags |= F_BOUNDARY;
		else
			flags &= (~F_BOUNDARY) & 0xff;
	}

	public boolean isBoundary() {
		return (flags & F_BOUNDARY) != 0;
	}

	/**
	 * Record the node's RouteCenter.
	 *
	 * We need to record this to determine whether arcs
	 * stay within the center. May pose a problem with
	 * respect to garbage collection.
	 */
	public void setRouteCenter(RouteCenter rc) {
		if (routeCenter != null)
			log.warn("resetting RouteCenter", nodeId);
		routeCenter = rc;
	}

	public RouteCenter getRouteCenter() {
		return routeCenter;
	}

	public void addArc(RouteArc arc) {
		if (!arcs.isEmpty())
			arc.setNewDir();
		arcs.add(arc);
	}

	/**
	 * Provide an upper bound to the size (in bytes) that
	 * writing this node will take.
	 *
	 * Should be called only after arcs and restrictions
	 * have been set. The size of arcs depends on whether
	 * or not they are internal to the RoutingCenter.
	 */
	public int boundSize() {
		// XXX: should perhaps better write() to some phantom writer
		// XXX: include size of restrictions
		return 1 + 1
			+ (haveLargeOffsets() ? 4 : 3)
			+ arcsSize() + restrSize();
	}

	private int arcsSize() {
		int s = 0;
		for (RouteArc arc : arcs) {
			s += arc.boundSize();
		}
		return s;
	}

	private int restrSize() {
		if (haveRestrictions()) {
			throw new Error("not implemented");
		} else
			return 0;
	}

	/**
	 * Writes a nod1 entry.
	 */
	public void write(ImgFileWriter writer) {
		log.debug("writing node, first pass, nod1", nodeId);
		offsetNod1 = writer.position();
		assert offsetNod1 < 0x1000000 : "node offset doesn't fit in 3 bytes";

		writer.put((byte) 0);  // will be overwritten later
		writer.put((byte) flags);

		if (haveLargeOffsets()) {
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

	/**
	 * Writes a nod3 entry.
	 */
	public void writeNod3(ImgFileWriter writer) {
		assert isBoundary() : "trying to write nod3 for non-boundary node";

		writer.put3(coord.getLongitude());
		writer.put3(coord.getLatitude());
		writer.put3(offsetNod1);
	}

	public int getOffsetNod1() {
		assert offsetNod1 != -1: "failed for node " + nodeId;
		return offsetNod1;
	}

	public void setOffsets(Coord centralPoint) {
		log.debug("center", centralPoint, ", coord", coord.toDegreeString());
		setLatOff(coord.getLatitude() - centralPoint.getLatitude());
		setLonOff(coord.getLongitude() - centralPoint.getLongitude());
	}

	public Coord getCoord() {
		return coord;
	}

	private void checkOffSize(int off) {
		if (off > 0x7ff || off < -0x800)
			// does off fit in signed 12 bit quantity?
			flags |= F_LARGE_OFFSETS;
		// does off fit in signed 16 bit quantity?
		assert (off <= 0x7fff && off >= -0x8000);
	}

	private void setLatOff(int latOff) {
		log.debug("lat off", Integer.toHexString(latOff));
		this.latOff = (char) latOff;
	}

	private void setLonOff(int lonOff) {
		log.debug("long off", Integer.toHexString(lonOff));
		this.lonOff = (char) lonOff;
	}

	/**
	 * Second pass over the nodes. Fill in pointers and Table A indices.
	 */
	public void writeSecond(ImgFileWriter writer) {
		for (RouteArc arc : arcs)
			arc.writeSecond(writer);
	}

	public Iterable<? extends RouteArc> arcsIteration() {
		return new Iterable<RouteArc>() {
			public Iterator<RouteArc> iterator() {
				return arcs.iterator();
			}
		};
	}

	public String toString() {
		return nodeId + "";
	}
}
