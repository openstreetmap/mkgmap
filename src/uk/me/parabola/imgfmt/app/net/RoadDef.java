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

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.IntList;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.OffsetWriterList;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.log.Logger;

/**
 * A road definition.  This ties together all segments of a single road
 * and provides street address information.
 *
 * @author Elrond
 * @author Steve Ratcliffe
 */
public class RoadDef {
	private static final Logger log = Logger.getLogger(RoadDef.class);

	private static final byte UNK1 = 0x04;
	private static final byte HAS_NOD_INFO = 0x40;

	private static final int MAX_LABELS = 4;

	private int offsetNod2;

	/** @deprecated bring in class? */
	private final OffsetWriterList owList = new OffsetWriterList();

	// There can be up to 4 labels for the same road.
	private final Label[] labels = new Label[MAX_LABELS];
	private int numlabels;

	private byte roadClass = (byte) 0x4d;
	private final byte roadFlags = (byte) 0x4;

	// The road length units may be affected by other flags in the header as
	// there is doubt as to the formula.
	private int roadLength = 300; // XXX set the road length

	@Deprecated // this may stay
	private List<RoadIndex> roadIndexes = new ArrayList<RoadIndex>();

	//// The label
	//private int labelOffset;
	private IntList rgnOffsets = new IntList(4);

	// This is the node associated with the road.  I'm not certain about how
	// this works, but in NOD2 each road has a reference to only one node.
	// This is that node.
	private RouteNode node;
	private int netPosition;

	/**
	 * Add a target location in the RGN section where we should write the
	 * offset of this road def when it is written to NET.
	 */
	public void addOffsetTarget(ImgFileWriter writer, int ormask) {
		//labelOffset = writer.position() | ormask;
		rgnOffsets.add(writer.position() | ormask);
		owList.addTarget(writer, ormask);
	}

	public void addPolylineRef(Polyline pl) {
		roadIndexes.add(new RoadIndex(pl));
	}

	private int getMaxZoomLevel() {
		int m = 0;
		for (RoadIndex ri : roadIndexes) {
			int z = ri.getZoomLevel();
			m = (z > m ? z : m);
		}
		return m;
	}

	/**
	 * This is for writing to NET1.
	 * @param writer A writer that is positioned within NET1.
	 */
	void writeNet1(ImgFileWriter writer) {
		//assert offset == realofs;
		if (numlabels == 0)
			return;
		assert numlabels > 0;

		netPosition = writer.position();

		byte flags = netFlags();

		writeLabels(writer);
		writer.put(flags);
		writer.put3(roadLength);

		int maxlevel = writeLevelCount(writer);

		writeLevelDivs(writer, maxlevel);

		if ((flags & HAS_NOD_INFO) != 0) {

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

	/**
	 * Write into the RGN the offset in net1 of this road.
	 * @param rgn A writer for the rgn file.
	 */
	void writeRgnOffsets(ImgFileWriter rgn) {
		int len = rgnOffsets.size();
		for (int i = 0; i < len; i++) {
			int off = rgnOffsets.get(i);
			rgn.position(off & 0x3fffff);
			rgn.put3(netPosition | (off & 0xc00000));
		}
	}

	private void writeLevelDivs(ImgFileWriter writer, int maxlevel) {
		for (int i = 0; i <= maxlevel; i++) {
			for (RoadIndex ri : roadIndexes) {
				if (ri.getZoomLevel() == i)
					ri.write(writer);
			}
		}
	}

	private int writeLevelCount(ImgFileWriter writer) {
		int maxlevel = getMaxZoomLevel();
		for (int i = 0; i <= maxlevel; i++) {
			byte b = 0;
			for (RoadIndex ri : roadIndexes) {
				if (ri.getZoomLevel() == i)
					b++;
			}
			if (i == maxlevel)
				b |= 0x80;
			writer.put(b);
		}
		return maxlevel;
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

	private byte netFlags() {
		byte flags = UNK1;
		if (node != null)
			flags |= HAS_NOD_INFO;
		return flags;
	}

	public void addLabel(Label l) {
		//if (numlabels >= MAX_LABELS)
		//	throw new IllegalStateException("Too many labels");
		if (numlabels == 0)
			labels[numlabels++] = l;
	}

	public void writeNod2(ImgFileWriter writer) {
		log.debug("writing nod2");
		offsetNod2 = writer.position();

		writer.put(roadClass);
		writer.put3(node.getOffsetNod1()); // offset to nod1

		// this is related to the number of nodes, but there is more to it...
		char nnodes = (char) 3;
		writer.putChar(nnodes);  // number of bits to follow
		writer.put((byte) ((1<<nnodes)-1));
	}

	public void setNode(RouteNode node) {
		this.node = node;
	}

	public int getNetPosition() {
		return netPosition;
	}
}
