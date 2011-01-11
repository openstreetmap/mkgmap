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

import java.util.HashMap;
import java.util.LinkedHashMap;

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

	// arcs for paved ways
	private final HashMap<Arc,Integer> pavedArcs = new LinkedHashMap<Arc,Integer>();
	// arcs for unpaved ways
	private final HashMap<Arc,Integer> unpavedArcs = new LinkedHashMap<Arc,Integer>();
	// arcs for ferry ways
	private final HashMap<Arc,Integer> ferryArcs = new LinkedHashMap<Arc,Integer>();

	private static int count;

	private boolean frozen ; // true when no more arcs should be added

	public TableA() {
		log.debug("creating TableA", count);
		count++;
	}

	/**
	 * Internal class tracking all the data a Table A entry needs.
	 * Basically a "forward arc".
	 */
	private class Arc {
		final RouteNode first; final RouteNode second;
		final RoadDef roadDef;

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
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;

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
	public void addArc(RouteArc arc) {
		assert !frozen : "trying to add arc to Table A after it has been frozen";
		Arc narc = new Arc(arc);
		int i;
		if(arc.getRoadDef().ferry()) {
			if (!ferryArcs.containsKey(narc)) {
				i = ferryArcs.size();
				ferryArcs.put(narc, i);
				log.debug("added ferry arc", count, narc, i);
			}
		}
		else if(arc.getRoadDef().paved()) {
			if (!pavedArcs.containsKey(narc)) {
				i = pavedArcs.size();
				pavedArcs.put(narc, i);
				log.debug("added paved arc", count, narc, i);
			}
		}
		else {
			if (!unpavedArcs.containsKey(narc)) {
				i = unpavedArcs.size();
				unpavedArcs.put(narc, i);
				log.debug("added unpaved arc", count, narc, i);
			}
		}
	}

	/**
	 * Retrieve an arc's index.
	 */
	public byte getIndex(RouteArc arc) {
		frozen = true;			// don't allow any more arcs to be added
		Arc narc = new Arc(arc);
		int i;
		if(arc.getRoadDef().ferry()) {
			assert ferryArcs.containsKey(narc):
			"Trying to read Table A index for non-registered arc: " + count + " " + narc;
			i = unpavedArcs.size() + ferryArcs.get(narc);
		}
		else if(arc.getRoadDef().paved()) {
			assert pavedArcs.containsKey(narc):
			"Trying to read Table A index for non-registered arc: " + count + " " + narc;
			i = unpavedArcs.size() + ferryArcs.size() + pavedArcs.get(narc);
		}
		else {
			assert unpavedArcs.containsKey(narc):
			"Trying to read Table A index for non-registered arc: " + count + " " + narc;
			i = unpavedArcs.get(narc);
		}
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
		return ferryArcs.size() + unpavedArcs.size() + pavedArcs.size();
	}

	public int numUnpavedArcs() {
		return unpavedArcs.size();
	}

	public int numFerryArcs() {
		return ferryArcs.size();
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
		assert size() < 0x100 : "Table A too large";
		return (byte)size();
	}

	/**
	 * This is called first to reserve enough space.  It will be rewritten
	 * later.
	 */
	public void write(ImgFileWriter writer) {
		offset = writer.position();
		int size = size() * ITEM_SIZE;
		log.debug("tab a offset", offset, "tab a size", size);
	
		for (int i = 0; i < size; i++)
			writer.put((byte) 0);
	}

	/**
	 * Fill in the table once the NET offsets of the roads are known.
	 */
	public void writePost(ImgFileWriter writer) {
		writer.position(offset);
		// unpaved arcs first
		for (Arc arc : unpavedArcs.keySet()) {
			writePost(writer, arc);
		}
		// followed by the ferry arcs
		for (Arc arc : ferryArcs.keySet()) {
			writePost(writer, arc);
		}
		// followed by the paved arcs
		for (Arc arc : pavedArcs.keySet()) {
			writePost(writer, arc);
		}
	}

	public void writePost(ImgFileWriter writer, Arc arc) {
		// write the table A entries.  Consists of a pointer to net
		// followed by 2 bytes of class and speed flags and road restrictions.
		int pos = arc.roadDef.getOffsetNet1();
		int access = arc.roadDef.getTabAAccess();
		// top bits of access go into net1 offset
		final int ACCESS_TOP_BITS = 0xc000;
		pos |= (access & ACCESS_TOP_BITS) << 8;
		access &= ~ACCESS_TOP_BITS;
		writer.put3(pos);
		writer.put((byte) arc.roadDef.getTabAInfo());
		writer.put((byte) access);
	}
}
