/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.log.Logger;

/**
 * A road definition.  This ties together all segments of a single road
 * and provides street address information.
 *
 * This corresponds to an entry in NET1, which is linked with the
 * polylines making up this road in RGN. Links to RGN are written
 * via RoadIndex, while writing links from RGN to NET1 is delayed
 * via setOffsetWriter.
 *
 * If the map includes routing, the NET1 record also points to
 * a NOD2 record, written by writeNod2.
 *
 * Edges in the routing graph ("arcs") link to the corresponding
 * road via the RoadDef, storing the NET1 offset via TableA,
 * which also includes some road information.
 *
 * @author Elrond
 * @author Steve Ratcliffe
 * @author Robert Vollmert
 */

public class RoadDef {
	private static final Logger log = Logger.getLogger(RoadDef.class);

	// the offset in Nod2 of our Nod2 record
	private int offsetNod2;

	// the offset in Net1 of our Net1 record
	private int offsetNet1;

	// for diagnostic purposes
	private final long roadId;
	private final String name;

	public RoadDef(long roadId, String name) {
		this.roadId = roadId;
		this.name = name;
	}

	// for diagnostic purposes
	public String toString() {
		return "RoadDef(" + name + ", " + roadId + ")";
	}

	/*
	 * Everything that's relevant for writing to NET1.
	 */

	private static final int NET_FLAG_NODINFO = 0x40;
	private static final int NET_FLAG_UNK1 = 0x04;
	private static final int NET_FLAG_ONEWAY = 0x02;
	private static final int NET_FLAG_ADDRINFO = 0x01;

	private int netFlags = NET_FLAG_UNK1;

	// The road length units may be affected by other flags in the header as
	// there is doubt as to the formula.
	private int roadLength;

	// There can be up to 4 labels for the same road.
	private static final int MAX_LABELS = 4;

	private final Label[] labels = new Label[MAX_LABELS];
	private int numlabels;

	private final SortedMap<Integer,List<RoadIndex>> roadIndexes = new TreeMap<Integer,List<RoadIndex>>();

	/**
	 * This is for writing to NET1.
	 * @param writer A writer that is positioned within NET1.
	 */
	void writeNet1(ImgFileWriter writer) {
		if (numlabels == 0)
			return;
		assert numlabels > 0;

		offsetNet1 = writer.position();

		writeLabels(writer);
		writer.put((byte) netFlags);
		writer.put3(roadLength);

		int maxlevel = writeLevelCount(writer);

		writeLevelDivs(writer, maxlevel);

		if (hasNodInfo()) {
			// This is the offset of an entry in NOD2
			int val = offsetNod2;
			if (val < 0x7fff) {
				writer.put((byte) 1);
				writer.putChar((char) val);
			} else {
				writer.put((byte) 2);
				writer.put3(val);
			}
		}
	}

	private void writeLabels(ImgFileWriter writer) {
		for (int i = 0; i < numlabels; i++) {
			Label l = labels[i];
			int ptr = l.getOffset();
			if (i == (numlabels-1))
				ptr |= 0x800000;
			writer.put3(ptr);
		}
	}

	private int writeLevelCount(ImgFileWriter writer) {
		int maxlevel = getMaxZoomLevel();
		for (int i = 0; i <= maxlevel; i++) {
			List<RoadIndex> l = roadIndexes.get(i);
			int b = (l == null) ? 0 : l.size();
			assert b < 0x80 : "too many polylines at level " + i;
			if (i == maxlevel)
				b |= 0x80;
			writer.put((byte) b);
		}
		return maxlevel;
	}

	private void writeLevelDivs(ImgFileWriter writer, int maxlevel) {
		for (int i = 0; i <= maxlevel; i++) {
			List<RoadIndex> l = roadIndexes.get(i);
			if (l != null) {
				for (RoadIndex ri : l)
					ri.write(writer);
			}
		}
	}

	public void addLabel(Label l) {
		// XXX: apparently, just one label for now?
		if (numlabels == 0)
			labels[numlabels++] = l;
		else if (!l.equals(labels[0]))
			log.warn("discarding extra label", l);
	}

	/**
	 * Add a polyline to this road.
	 *
	 * References to these are written to NET. At a given zoom
	 * level, we're writing these in the order we get them,
	 * which possibly needs to be the order the segments have
	 * in the road.
	 */
	public void addPolylineRef(Polyline pl) {
		log.debug("adding polyline ref", this, pl.getSubdiv());
		int level = pl.getSubdiv().getZoom().getLevel();
		List<RoadIndex> l = roadIndexes.get(level);
		if (l == null) {
			l = new ArrayList<RoadIndex>();
			roadIndexes.put(level, l);
		}
		int s = l.size();
		if (s > 0)
			l.get(s-1).getLine().setLastSegment(false);
		l.add(new RoadIndex(pl));
	}

	private int getMaxZoomLevel() {
		return roadIndexes.lastKey();
	}


	/**
	 * Set the road length (in meters).
	 */
	public void setLength(double l) {
		// XXX: this is from test.display.NetDisplay, possibly varies
		roadLength = (int) l / 2;
	}

	/*
	 * Everything that's relevant for writing to RGN.
	 */

	class Offset {
		final int position;
		final int flags;

		Offset(int position, int flags) {
			this.position = position;
			this.flags = flags;
		}

		int getPosition() {
			return position;
		}

		int getFlags() {
			return flags;
		}
	}

	private final List<Offset> rgnOffsets = new ArrayList<Offset>(4);

	/**
	 * Add a target location in the RGN section where we should write the
	 * offset of this road def when it is written to NET.
	 *
	 * @param position The offset in RGN.
	 * @param flags The flags that should be set.
	 */
	public void addOffsetTarget(int position, int flags) {
		rgnOffsets.add(new Offset(position, flags));
	}

	/**
	 * Write into the RGN the offset in net1 of this road.
	 * @param rgn A writer for the rgn file.
	 */
	void writeRgnOffsets(ImgFileWriter rgn) {
		assert offsetNet1 < 0x400000 : "NET 1 offset too large";
		for (Offset off : rgnOffsets) {
			rgn.position(off.getPosition());
			rgn.put3(offsetNet1 | off.getFlags());
		}
	}

	private boolean internalNodes = true;

	/**
	 * Does the road have any nodes besides start and end?
	 *
	 * This affects whether we need to write extra bits in
	 * the bitstream in RGN.
	 */
	public boolean hasInternalNodes() {
		return internalNodes;
	}

	public void setInternalNodes(boolean n) {
		internalNodes = n;
	}

	/*
	 * Everything that's relevant for writing out Nod 2.
	 */

	// This is the node associated with the road.  I'm not certain about how
	// this works, but in NOD2 each road has a reference to only one node.
	// This is that node.
	private RouteNode node;

	// the first point in the road is a node (the above routing node)
	private boolean startsWithNode = true;
	// number of nodes in the road
	private int nnodes;

	public static final int NOD2_MASK_SPEED = 0x0e;
	public static final int NOD2_MASK_CLASS = 0xf0; // might be less
	private static final int NOD2_FLAG_UNK = 0x01;

	// always appears to be set
	private int nod2Flags = NOD2_FLAG_UNK;

	/**
	 * Set the routing node associated with this road.
	 *
	 * This implies that the road has an entry in NOD 2
	 * which will be pointed at from NET 1.
	 */
	public void setNode(RouteNode node) {
		netFlags |= NET_FLAG_NODINFO;
		this.node = node;
	}

	private boolean hasNodInfo() {
		return (netFlags & NET_FLAG_NODINFO) != 0;
	}

	public void setStartsWithNode(boolean s) {
		startsWithNode = s;
	}

	public void setNumNodes(int n) {
		nnodes = n;
	}

	/**
	 * Write this road's NOD2 entry.
	 *
	 * Stores the writing position to be able to link here
	 * from NET 1 later.
	 *
	 * @param writer A writer positioned in NOD2.
	 */
	public void writeNod2(ImgFileWriter writer) {
		if (!hasNodInfo())
			return;

		log.debug("writing nod2");

		offsetNod2 = writer.position();

		writer.put((byte) nod2Flags);
		writer.put3(node.getOffsetNod1()); // offset in nod1

		// this is related to the number of nodes, but there
		// is more to it...
		// For now, shift by one if the first node is not a
		// routing node. Supposedly, other holes are also
		// possible.
		// This might be unnecessary if we just make sure
		// that every road starts with a node.
		int nbits = nnodes;
		if (!startsWithNode)
			nbits++;
		writer.putChar((char) nbits);
		boolean[] bits = new boolean[nbits];
		for (int i = 0; i < bits.length; i++)
			bits[i] = true;
		if (!startsWithNode)
			bits[0] = false;
		for (int i = 0; i < bits.length; i += 8) {
			int b = 0;
            for (int j = 0; j < 8 && j < bits.length - i; j++)
				if (bits[i+j])
					b |= 1 << j;
			writer.put((byte) b);
		}
	}

	/*
	 * Everything that's relevant for writing out Table A.
	 *
	 * Storing this info in the RoadDef means that each
	 * arc gets the same version of the below info, which
	 * makes sense for the moment considering polish format
	 * doesn't provide for different speeds and restrictions
	 * for segments of roads.
	 */

	/**
	 * Return the offset of this road's NET1 entry. Assumes
	 * writeNet1() has been called.
	 */
	public int getOffsetNet1() {
		return offsetNet1;
	}

	// first byte of Table A info in NOD 1
	private static final int TABA_FLAG_TOLL = 0x80;
	private static final int TABA_MASK_CLASS = 0x70;
	private static final int TABA_FLAG_ONEWAY = 0x08;
	private static final int TABA_MASK_SPEED = 0x07;
	// second byte: access flags, sorted as in .mp
	// bits 0x08, 0x80 missing (purpose unknown)
	private static final int[] ACCESS = {
		0x4000, // emergency (net pointer bit 30)
		0x8000, // delivery (net pointer bit 31)
		0x0001, // car
		0x0002, // bus
		0x0004, // taxi
		0x0010, // foot
		0x0020, // bike
		0x0040, // truck
	};

	// The data for Table A
	private int tabAInfo; 
	private int tabAAccess;

	public void setToll() {
		tabAInfo |= TABA_FLAG_TOLL;
	}

	public void setAccess(boolean[] access) {
		tabAAccess = 0;
		for (int i = 0; i < 8; i++)
			if (access[i])
				tabAAccess |= ACCESS[i];
	}

	public int getTabAInfo() {
		return tabAInfo;
	}

	public int getTabAAccess() {
		return tabAAccess;
	}

	/*
	 * These affect various parts.
	 */

	private int roadClass = -1;

	// road class that goes in various places (really?)
	public void setRoadClass(int roadClass) {
		assert roadClass < 0x08;

		/* for RouteArcs to get as their "destination class" */
		this.roadClass = roadClass;

		/* for Table A */
		int shifted = (roadClass << 4) & 0xff;
		tabAInfo |= shifted;

		/* for NOD 2 */
		nod2Flags |= shifted;
	}

	public int getRoadClass() {
		assert roadClass >= 0 : "roadClass not set";
		return roadClass;
	}

	public void setSpeed(int speed) {
		assert speed < 0x08;

		/* for Table A */
		tabAInfo |= speed;

		/* for NOD 2 */
		nod2Flags |= (speed << 1);
	}

	public void setOneway() {
		tabAInfo |= TABA_FLAG_ONEWAY;
		netFlags |= NET_FLAG_ONEWAY;
	}
}
