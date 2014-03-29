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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A restriction in the routing graph.
 *
 * A routing restriction has two or more arcs.
 * The first arc is the "from" arc, the last is the "to" arc,
 *  and other arc is a "via" arc.
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

	// first three bytes of the header -- might specify the type of restriction
	// and when it is active
	private static final byte RESTRICTION_TYPE = 0x05; // 0x07 spotted, meaning?

	// To specify that a node is given by a relative offset instead
	// of an entry to Table B.
	private static final int F_INTERNAL = 0x8000;

	// the arcs
	private final List<RouteArc> arcs;

	private final RouteNode viaNode; 
	// offset in Table C
	private byte offsetSize;
	private int offsetC;

	// last restriction in a node
	private boolean last;

	// mask that specifies which vehicle types the restriction doesn't apply to
	private final byte exceptMask;
	private final byte flags; // meaning of bits 0x01 and 0x10 are not clear 

	private final static byte F_EXCEPT_FOOT      = 0x02;
	private final static byte F_EXCEPT_EMERGENCY = 0x04;
	private final static byte F_MORE_EXCEPTIONS  = 0x08;
	
	public final static byte EXCEPT_CAR      = 0x01;
	public final static byte EXCEPT_BUS      = 0x02;
	public final static byte EXCEPT_TAXI     = 0x04;
	public final static byte EXCEPT_DELIVERY = 0x10;
	public final static byte EXCEPT_BICYCLE  = 0x20;
	public final static byte EXCEPT_TRUCK    = 0x40;
	
	// additional flags that can be passed via exceptMask  
	public final static byte EXCEPT_FOOT      = 0x08; // not written as such
	public final static byte EXCEPT_EMERGENCY = (byte)0x80; // not written as such
	private final static byte SPECIAL_EXCEPTION_MASK = ~(EXCEPT_FOOT|EXCEPT_EMERGENCY);


	public RouteRestriction(RouteNode viaNode, List<RouteArc> traffArcs, byte exceptMaskParm) {
		this.viaNode = viaNode;
		this.arcs = new ArrayList<RouteArc>(traffArcs);
		byte flags = 0;
		if ((exceptMaskParm & EXCEPT_FOOT) != 0)
			flags |= F_EXCEPT_FOOT;
		if ((exceptMaskParm & EXCEPT_EMERGENCY) != 0)
			flags |= F_EXCEPT_EMERGENCY;
		
		exceptMask = (byte)(exceptMaskParm & SPECIAL_EXCEPTION_MASK); 
		if(exceptMask != 0)
			flags |= F_MORE_EXCEPTIONS;

		int numArcs = arcs.size();
		assert numArcs < 8;
		flags |= ((numArcs) << 5);
		this.flags = flags;
	}

	
	private int calcOffset(RouteNode node, int tableOffset) {
		int offset = tableOffset - node.getOffsetNod1();
		assert offset >= 0 : "node behind start of tables";
		assert offset < 0x8000 : "node offset too large";
		return offset | F_INTERNAL;
	}

	public List<RouteArc> getArcs(){
		return arcs;
	}
	/**
	 * Writes a Table C entry with 3 or 4 nodes.
	 *
	 * @param writer The writer.
	 * @param tableOffset The offset in NOD 1 of the tables area.
	 */
	public void write(ImgFileWriter writer, int tableOffset) {
		writer.put(RESTRICTION_TYPE); 

		writer.put(flags);
		writer.put((byte)0); // meaning ?

		if(exceptMask != 0)
			writer.put(exceptMask);

		int numArcs = arcs.size();
		int[] offsets = new int[numArcs+1];
		// first arc is inverse arc
		int pos = 0;
		boolean viaWritten = false;
		for (int i = 0; i < numArcs; i++){
			RouteArc arc = arcs.get(i);
			if (arc.getSource() == viaNode){
				if (arc.isInternal())
					offsets[pos++] = calcOffset(arc.getDest(), tableOffset);
				else 
					offsets[pos++] = arc.getIndexB();
				if (!viaWritten){
					offsets[pos++] = calcOffset(viaNode, tableOffset);
					viaWritten = true;
				}
			} else {
				if (arc.isInternal())
					offsets[pos++] = calcOffset(arc.getDest(), tableOffset);
				else 
					offsets[pos++] = arc.getIndexB();
			}
		}
		for (int i = 1; i < offsets.length; i++){
			if (offsets[i-1] == offsets[i]){
				assert false: "failed to calculate offsets";
			}
		}
		for (int offset : offsets)
			writer.putChar((char) offset);

		for (RouteArc arc: arcs)
			writer.put(arc.getIndexA());
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
		int size = 3; // header length
		if(exceptMask != 0)
			++size;
		size += arcs.size() + (arcs.size()+1) * 2; 
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

	public void setLast() {
		last = true;
	}
}
