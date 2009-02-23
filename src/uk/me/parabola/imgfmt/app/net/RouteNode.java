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
public class RouteNode implements Comparable {
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
	private static final int F_ARCS = 0x40;
	private static final int F_UNK_NEEDED = 0x04; // XXX

	private int offsetNod1 = -1;

	@Deprecated
	private final int nodeId; // XXX not needed at this point?

	// arcs from this node
	private final List<RouteArc> arcs = new ArrayList<RouteArc>();
	// restrictions at (via) this node
	private final List<RouteRestriction> restrictions = new ArrayList<RouteRestriction>();
	
	private int flags = F_UNK_NEEDED;

	private final CoordNode coord;
	private char latOff;
	private char lonOff;

	// this is for setting destination class on arcs
	// we're taking the maximum of roads this node is
	// on for now -- unsure of precise mechanic
	private int nodeClass;

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
		return !restrictions.isEmpty();
	}

	protected void setBoundary(boolean b) {
		if (b)
			flags |= F_BOUNDARY;
		else
			flags &= (~F_BOUNDARY) & 0xff;
	}

	public boolean isBoundary() {
		return (flags & F_BOUNDARY) != 0;
	}

	public void addArc(RouteArc arc) {
		if (!arcs.isEmpty())
			arc.setNewDir();
		arcs.add(arc);
		int cl = arc.getRoadDef().getRoadClass();
		log.debug("adding arc", arc.getRoadDef(), cl);
		if (cl > nodeClass)
			nodeClass = cl;
		flags |= F_ARCS;
	}

	public void addRestriction(RouteRestriction restr) {
		restrictions.add(restr);
		flags |= F_RESTRICTIONS;
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
		return 2*restrictions.size();
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
			writer.putInt((latOff << 16) | (lonOff & 0xffff));
		} else {
			writer.put3((latOff << 12) | (lonOff & 0xfff));
		}

		if (!arcs.isEmpty()) {
			arcs.get(arcs.size() - 1).setLast();
			for (RouteArc arc : arcs)
				arc.write(writer);
		}

		if (!restrictions.isEmpty()) {
			restrictions.get(restrictions.size() - 1).setLast();
			for (RouteRestriction restr : restrictions)
				restr.writeOffset(writer);
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
		checkOffSize(latOff);
	}

	private void setLonOff(int lonOff) {
		log.debug("long off", Integer.toHexString(lonOff));
		this.lonOff = (char) lonOff;
		checkOffSize(lonOff);
	}

	/**
	 * Second pass over the nodes. Fill in pointers and Table A indices.
	 */
	public void writeSecond(ImgFileWriter writer) {
		for (RouteArc arc : arcs)
			arc.writeSecond(writer);
	}

	/**
	 * Return the node's class, which is the maximum of
	 * classes of the roads it's on.
	 */
	public int getNodeClass() {
		return nodeClass;
	}

	public Iterable<? extends RouteArc> arcsIteration() {
		return new Iterable<RouteArc>() {
			public Iterator<RouteArc> iterator() {
				return arcs.iterator();
			}
		};
	}

	public List<RouteRestriction> getRestrictions() {
		return restrictions;
	}

	public String toString() {
		return nodeId + "";
	}

	/*
	 * For sorting node entries in NOD 3.
	 */
	public int compareTo(Object o) {
		Coord other = ((RouteNode) o).getCoord();
		return coord.compareTo(other);
	}
}
