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
	//private static final Logger log = Logger.getLogger(RouteRestriction.class);

	// size in bytes
	private static final int SIZE = 11;

	// first three bytes of the header -- might specify the type of restriction
	// and when it is active
	private static final int HEADER = 0x004005;

	// To specifiy that a node is given by a relative offset instead
	// of an entry to Table B.
	private static final int F_INTERNAL = 0x8000;

	// the arcs
	private final RouteArc from;
	private final RouteArc to;

	// offset in Table C
	private byte offsetSize;
	private int offsetC;

	// last restriction in a node
	private boolean last;

	// mask that specifies which vehicle types the restriction doesn't apply to
	private final byte exceptMask;

	public final static byte EXCEPT_CAR      = 0x01;
	public final static byte EXCEPT_BUS      = 0x02;
	public final static byte EXCEPT_TAXI     = 0x04;
	public final static byte EXCEPT_DELIVERY = 0x10;
	public final static byte EXCEPT_BICYCLE  = 0x20;
	public final static byte EXCEPT_TRUCK    = 0x40;

	/**
	 * Create a route restriction.
	 *
	 * @param from The inverse arc of "from" arc.
	 * @param to The "to" arc.
	 */
	public RouteRestriction(RouteArc from, RouteArc to, byte exceptMask) {
		assert from.getSource().equals(to.getSource()) : "arcs in restriction don't meet";
		this.from = from;
		this.to = to;
		this.exceptMask = exceptMask;
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
		int header = HEADER;

		if(exceptMask != 0)
			header |= 0x0800;

		writer.put3(header);

		if(exceptMask != 0)
			writer.put(exceptMask);

		int[] offsets = new int[3];

		if (from.isInternal())
			offsets[0] = calcOffset(from.getDest(), tableOffset);
		else
			offsets[0] = from.getIndexB();
		offsets[1] = calcOffset(to.getSource(), tableOffset);
		if (to.isInternal())
			offsets[2] = calcOffset(to.getDest(), tableOffset);
		else
			offsets[2] = to.getIndexB();

		for (int offset : offsets)
			writer.putChar((char) offset);

		writer.put(from.getIndexA());
		writer.put(to.getIndexA());
	}

	/**
	 * Write this restriction's offset within Table C into a node record.
	 */
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
		int size = SIZE;
		if(exceptMask != 0)
			++size;
		return size;
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
