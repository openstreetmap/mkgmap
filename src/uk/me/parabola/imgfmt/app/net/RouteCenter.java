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

import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * Routing nodes are divided into areas which I am calling RouteCenter's.
 * The center has a location and it contains nodes that are nearby.
 * There is routing between nodes in the center and there are links
 * to nodes in other centers.
 */
public class RouteCenter {
	private static final Logger log = Logger.getLogger(RouteCenter.class);

	private final Coord centralPoint;

	private final List<RouteNode> nodes;

	private final TableA tabA;
	private final TableB tabB;
	private final TableC tabC;

	public RouteCenter(Coord cp, List<RouteNode> nodes,
				TableA tabA, TableB tabB) {
		log.info("new RouteCenter at " + cp.toDegreeString() + ", nodes: " + nodes.size()
						+ " tabA: " + tabA.size() + " tabB: " + tabB.size());

		this.centralPoint = cp;
		this.nodes = nodes;
		this.tabA = tabA;
		this.tabB = tabB;
		this.tabC = new TableC();

		// update lat/lon offsets; update arcs with table indices; populate tabC
		for (RouteNode node : nodes) {
			node.setOffsets(centralPoint);
			for (RouteArc arc : node.arcsIteration()) {
				arc.setIndexA(tabA.getIndex(arc));
				if (!arc.isInternal())
					arc.setIndexB(tabB.getIndex(arc.getDest()));
			}
			for (RouteRestriction restr : node.getRestrictions())
				restr.setOffsetC(tabC.addRestriction(restr));
		}
		// update size of tabC offsets, now that tabC has been populated
		tabC.propagateSizeBytes();
	}

	/**
	 * Write a route center.
	 *
	 * writer.position() is relative to the start of NOD 1.
	 * Space for Table A is reserved but not written. See writeTableA.
	 */
	public void write(ImgFileWriter writer) {
		assert !nodes.isEmpty(): "RouteCenter without nodes";

		for (RouteNode node : nodes)
			node.write(writer);

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
		writer.put(tabC.getSizeBytes());
		writer.put3(centralPoint.getLongitude());
		writer.put3(centralPoint.getLatitude());
		writer.put(tabA.getNumberOfItems());
		writer.put(tabB.getNumberOfItems());

		tabA.write(writer);
		tabB.write(writer);
		tabC.write(writer, tablesOffset);
		log.info("end of center:", writer.position());
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
