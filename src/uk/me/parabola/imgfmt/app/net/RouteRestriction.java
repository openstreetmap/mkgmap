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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * A restriction in the routing graph.
 *
 * There may eventually be several types of these at which point
 * we might consider splitting them into several classes. For the
 * moment, just simple from-to-via restrictions.
 *
 * A from-to-via restriction says you can't go along arc "to"
 * if you came to node to.getSource() == from.getSource()
 * via the inverse arc of "from". We're using the inverse of
 * "from" since that has the information we need for writing
 * the Table C entry.
 *
 * @author Robert Vollmert
 */
public class RouteRestriction {
	private static final Logger log = Logger.getLogger(RouteRestriction.class);

	// size in bytes
	public static final int SIZE = 11;

	// first three bytes of the header -- might specify the type of restriction
	// and when it is active
	private static final int HEADER = 0x054000;

	// To specifiy that a node is given by a relative offset instead
	// of an entry to Table B.
	private static final int F_INTERNAL = 0x8000;

	// the arcs
	private RouteArc from;
	private RouteArc to;

	// offset in Table C
	private byte offsetSize;
	private int offsetC;

	// last restriction in a node
	private boolean last = false;

	/**
	 * Create a route restriction.
	 *
	 * @param from The inverse arc of "from" arc.
	 * @param to The "to" arc.
	 */
	public RouteRestriction(RouteArc from, RouteArc to) {
		assert from.getSource().equals(to.getSource()) : "arcs in restriction don't meet";
		this.from = from;
		this.to = to;
	}

	private int calcOffset(RouteNode node, int tableOffset) {
		int offset = tableOffset - node.getOffsetNod1();
		assert offset >= 0 : "node behind start of tables";
		assert offset < 0x8000 : "node offset too large";
		return offset | F_INTERNAL;
	}

	/**
	 * Writes a Table C entry.
	 *
	 * @param writer The writer.
	 * @param tableOffset The offset in NOD 1 of the tables area.
	 */
	public void write(ImgFileWriter writer, int tableOffset) {
		writer.put3(HEADER);
		int[] offsets = new int[3];

		if (from.isInternal())
			offsets[0] = calcOffset(from.getDest(), tableOffset);
		else
			offsets[0] = from.getIndexB();
		offsets[1] = calcOffset(from.getDest(), tableOffset);
		if (to.isInternal())
			offsets[0] = calcOffset(to.getDest(), tableOffset);
		else
			offsets[0] = to.getIndexB();

		for (int i = 0; i < offsets.length; i++)
			writer.putChar((char) offsets[i]);

		writer.put((byte) from.getIndexA());
		writer.put((byte) to.getIndexA());
	}

	public void writeOffset(ImgFileWriter writer) {
		assert 0 < offsetSize && offsetSize <= 2 : "illegal offset size";
		int offset = offsetC;
		if (offsetSize == 1) {
			assert offset < 0x80;
			if (last)
				offset |= 0x80;
			writer.put((byte) offset);
		} else {
			assert offset < 0x8000;
			if (last)
				offset |= 0x8000;
			writer.putChar((char) offset);
		}
	}

	/**
	 * Size in bytes of the Table C entry.
	 */
	public int getSize() {
		return SIZE;
	}

	public void setOffsetC(int offsetC) {
		this.offsetC = offsetC;
	}

	public int getOffsetC() {
		return offsetC;
	}

	public void setOffsetSize(byte size) {
		offsetSize = size;
	}

	public byte getOffsetSize() {
		return offsetSize;
	}

	public void setLast() {
		last = true;
	}
}
