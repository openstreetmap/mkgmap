/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * 
 * Author: Steve Ratcliffe
 * Create date: 18-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.LinkedHashMap;

import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * Table A that contains road information for segments in one RouteCenter.
 *
 * Each arc starting from a node in the RouteCenter has an associated
 * entry in Table A, shared by the inverse arc for internal arcs. This
 * entry consists of some routing parameters and a link to the road in
 * NET.
 */
public class TableA {
	private static final Logger log = Logger.getLogger(TableA.class);

	private static final int ITEM_SIZE = 5;

	// This table's start position relative to the start of NOD 1
	private int offset;

	private LinkedHashMap<Arc,Integer> arcs = new LinkedHashMap<Arc,Integer>();
	private int size = 0;

	private static int count = 0;
	public TableA() {
		log.debug("creating TableA", count);
		count++;
	}

	/**
	 * Internal class tracking all the data a Table A entry needs.
	 * Basically a "forward arc".
	 */
	private class Arc {
		RouteNode first, second;
		RoadDef roadDef;

		Arc(RouteArc arc) {
			if (arc.isForward()) {
				first = arc.getSource();
				second = arc.getDest();
			} else {
				first = arc.getDest();
				second = arc.getSource();
			}
			roadDef = arc.getRoadDef();
		}

		public boolean equals(Object obj) {
			Arc arc = (Arc) obj;
			return first.equals(arc.first)
				&& second.equals(arc.second)
				&& roadDef.equals(arc.roadDef);
		}

		public int hashCode() {
			return first.hashCode() + 2*second.hashCode() + roadDef.hashCode();
		}

		public String toString() {
			return "" + first + "->" + second + " (" + roadDef + ")";
		}
	}

	/**
	 * Add an arc to the table if not present and set its index.
	 *
	 * The value may overflow while it isn't certain that
	 * the table fulfills the size constraint.
	 */
	public byte addArc(RouteArc arc) {
		Arc narc = new Arc(arc);
		int i;
		if (!arcs.containsKey(narc)) {
			i = arcs.size();
			arcs.put(narc, new Integer(i));
			log.debug("added arc", count, narc, i);
		} else {
			i = arcs.get(narc);
		}
		return (byte) i;
	}

	/**
	 * Retrieve an arc's index.
	 */
	public byte getIndex(RouteArc arc) {
		log.debug("getting index", arc);
		Arc narc = new Arc(arc);
		assert arcs.containsKey(narc):
			"Trying to read Table A index for non-registered arc: " + count + " " + narc;
		int i = arcs.get(narc).intValue();
		assert i < 0x100 : "Table A index too large: " + narc;
		return (byte) i;
	}

	/**
	 * Retrieve the size of the Table as an int.
	 *
	 * While Table A is limited to byte size (0x100 entries),
	 * we temporarily build larger tables while subdividing
	 * the network.
	 */
	public int size() {
		return arcs.size();
	}

	/**
	 * Retrieve the size of the table as byte.
	 *
	 * This value is what should be written to the table
	 * header. When this is read, the table is assumed to
	 * be fit for writing, so at this point we check
	 * it isn't too large.
	 */
	public byte getNumberOfItems() {
		assert arcs.size() < 0x100 : "Table A too large";
		return (byte) arcs.size();
	}

	/**
	 * This is called first to reserve enough space.  It will be rewritten
	 * later.
	 */
	public void write(ImgFileWriter writer) {
		offset = writer.position();
		int size = arcs.size() * ITEM_SIZE;		
		log.debug("tab a offset", offset, "tab a size", size);
	
		writer.position(writer.position() + size);
	}

	/**
	 * Fill in the table once the NET offsets of the roads are known.
	 */
	public void writePost(ImgFileWriter writer) {
		writer.position(offset);
		for (Arc arc : arcs.keySet()) {
			// write the table A entries.  Consists of a pointer to net
			// followed by 2 bytes of class and speed flags and road restrictions.
			log.debug("writing Table A entry", arcs.get(arc));
			int pos = arc.roadDef.getNetPosition();
			writer.put3(pos);
			writer.put((byte) 0x46);
			writer.put((byte) 0x0);
		}
	}
}
