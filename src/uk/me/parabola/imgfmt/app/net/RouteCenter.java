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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.RoadNetwork;

/**
 * Routing nodes are divided into areas which I am calling RouteCenter's.
 * The center has a location and it contains nodes that are nearby.
 * There is routing between nodes in the center and there are links
 * to nodes in other centers.
 */
public class RouteCenter {
	private static final Logger log = Logger.getLogger(RouteCenter.class);

	private final Coord centralPoint;

	// some heuristics so the center doesn't get too large
	private int estimatedNodesSize = 0;
	// absolute upper bound (would need to reduce some)
	//private final int maxNodesSize = (1 << NODHeader.DEF_ALIGN) * 0x100;
	// the "low bytes" don't tend do go above 0x30, so we'll try smaller than that for now
	private final int maxNodesSize = (1 << NODHeader.DEF_ALIGN) * 0x30;

	private final List<RouteNode> nodes = new ArrayList<RouteNode>();

	// These may be pulled into this class
	//private Tables tables = new Tables();
	private final TableA tabA = new TableA();
	private final TableB tabB = new TableB();
	private final TableC tabC = new TableC();

	public RouteCenter(Coord cp) {
		this.centralPoint = cp;
	}

	public boolean nodeFits(RouteNode node) {
		return estimatedNodesSize + node.boundSize() <= maxNodesSize;
	}

	public void addNode(RouteNode node, Coord coord) {
		estimatedNodesSize += node.boundSize();
		assert estimatedNodesSize <= maxNodesSize : "RouteCenter full";

		node.setOffsets(centralPoint);
		node.setRouteCenter(this);
		nodes.add(node);
	}

	/**
	 * Check which arcs are within the centers and do what needs to be done.
	 *
	 * Update arcs' INTER_AREA flag.
	 * Prepare data for Table B.
	 */
	private void handleInterCenterArcs() {
		for (RouteNode node : nodes) {
			for (RouteArc arc : node.arcsIteration()) {
				boolean internal = arc.getDest().getRouteCenter() == this;
				arc.setInternal(internal);
				if (internal) {
					tabA.addArc(arc);
				} else {					
					byte idxB = tabB.addNode(arc.getDest());
					arc.setIndexB(idxB);
				}
			}
		}
	}			

	/**
	 * Write a route center.
	 *
	 * writer.position() is relative to the start of NOD 1.
	 * Space for Table A is reserved but not written. See writeTableA.
	 */
	public void write(ImgFileWriter writer) {
		assert !nodes.isEmpty(): "RouteCenter without nodes";

		// we have all our nodes now
		handleInterCenterArcs();

		for (RouteNode node : nodes) {
			node.write(writer);

			// save table A entries
			for (RouteArc arc : node.arcsIteration())
				tabA.addArc(arc);
		}

		int mult = 1 << NODHeader.DEF_ALIGN;

		// Get the position of the tables, and position there.
		int roundpos = (writer.position() + mult - 1) 
					>> NODHeader.DEF_ALIGN
					<< NODHeader.DEF_ALIGN;
		int tablesOffset = roundpos + mult;
		log.debug("write table a at offset", Integer.toHexString(tablesOffset));

		// Go back and fill in all the table offsets
		for (RouteNode node : nodes) {
			int pos = node.getOffsetNod1();
			log.debug("node pos", pos);
			byte bo = (byte) calcLowByte(pos, tablesOffset);

			writer.position(pos);
			log.debug("rewrite taba offset", writer.position(), bo);
			writer.put(bo);

			// fill in arc pointers
			node.writeSecond(writer);
		}

		writer.position(tablesOffset);

		// Write the tables header
		writer.put(tabC.getSize());
		writer.put3(centralPoint.getLongitude());
		writer.put3(centralPoint.getLatitude());
		writer.put((byte) tabA.getNumberOfItems());
		writer.put((byte) tabB.getSize()); // number of table B entries

		tabA.write(writer);
		tabB.write(writer);
		tabC.write(writer);
		log.info("endof node " + writer.position());
	}

	public void writePost(ImgFileWriter writer) {
		// NET addresses are now known
		tabA.writePost(writer);
		// all RouteNodes now have their NOD1 offsets
		tabB.writePost(writer);
	}

	/**
	 * Calculate the offset of the Tables in NOD 1 given the offset
	 * of a node and its "low byte".
	 */
	public static int calcTableOffset(int nodeOffset, int low) {
		assert low >= 0 && low < 0x100;
		int align = NODHeader.DEF_ALIGN;

		int off = nodeOffset >> align;
		return (off + 1 + low) << align;

        }

	/**
	 * Inverse of calcTableOffset.
	 */
	public static int calcLowByte(int nodeOffset, int tablesOffset) {
		assert nodeOffset < tablesOffset;
		int align = NODHeader.DEF_ALIGN;
		int mask = (1 << align) - 1;
		if ((tablesOffset & mask) != 0) {
			log.warn("tablesOffset not a multiple of (1<<align): %x", tablesOffset);
			// round up to next multiple
			tablesOffset = ((tablesOffset >> align) + 1) << align;
		}
		int low = (tablesOffset >> align) - (nodeOffset >> align) - 1;
		assert 0 <= low && low < 0x100;
		return low;
	}
}
