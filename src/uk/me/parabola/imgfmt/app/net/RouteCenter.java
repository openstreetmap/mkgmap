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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
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

	private final Area area;
	private final Coord centralPoint;

	private final List<RouteNode> nodes;

	private final TableA tabA;
	private final TableB tabB;
	private final TableC tabC;

	public RouteCenter(Area area, List<RouteNode> nodes,
					   TableA tabA, TableB tabB) {

		this.area = area;
		this.centralPoint = area.getCenter();
		this.nodes = nodes;
		this.tabA = tabA;
		this.tabB = tabB;
		this.tabC = new TableC(tabA);

		log.info("new RouteCenter at " + centralPoint.toDegreeString() +
				 ", nodes: " + nodes.size()	+ " tabA: " + tabA.size() +
				 " tabB: " + tabB.size());
	}

	/**
	 * update arcs with table indices; populate tabC
	 */
	private void updateOffsets(){
		for (RouteNode node : nodes) {
			node.setOffsets(centralPoint);
			for (RouteArc arc : node.arcsIteration()) {
				arc.setIndexA(tabA.getIndex(arc));
				arc.setInternal(nodes.contains(arc.getDest()));
				if (!arc.isInternal())
					arc.setIndexB(tabB.getIndex(arc.getDest()));
			}

			for (RouteRestriction restr : node.getRestrictions()){
				if (restr.getArcs().size() >= 3){
					// only restrictions with more than 2 arcs can contain further arcs 
					for (RouteArc arc : restr.getArcs()){
						if (arc.getSource() == node)
							continue;
						arc.setIndexA(tabA.getIndex(arc));
						arc.setInternal(nodes.contains(arc.getDest()));
						if (!arc.isInternal())
							arc.setIndexB(tabB.getIndex(arc.getDest()));
					}
				}
				restr.setOffsetC(tabC.addRestriction(restr));
			}
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
	public void write(ImgFileWriter writer, int[] classBoundaries) {
		assert !nodes.isEmpty(): "RouteCenter without nodes";
		updateOffsets();
		int centerPos = writer.position();
		for (RouteNode node : nodes){
			node.write(writer);
			int group = node.getGroup();
			if (group == 0)
				continue;
			if (centerPos < classBoundaries[group-1]){
				// update positions (loop is used because style might not use all classes  
				for (int i = group-1; i >= 0; i--){
					if (centerPos < classBoundaries[i] )
						classBoundaries[i] = centerPos;
				}
			}
		}
		int alignment = 1 << NODHeader.DEF_ALIGN;
		int alignMask = alignment - 1;

		// Calculate the position of the tables.
		int tablesOffset = (writer.position() + alignment) & ~alignMask;
		log.debug("write table a at offset", Integer.toHexString(tablesOffset));

		// Go back and fill in all the table offsets
		for (RouteNode node : nodes) {
			int pos = node.getOffsetNod1();
			log.debug("node pos", pos);
			byte bo = (byte) calcLowByte(pos, tablesOffset);

			writer.position(pos);
			log.debug("rewrite taba offset", writer.position(), bo);
			writer.put1u(bo);

			// fill in arc pointers
			node.writeSecond(writer);
		}

		writer.position(tablesOffset);

		// Write the tables header
		writer.put1u(tabC.getFormat());
		Utils.put3sLongitude(writer, centralPoint.getLongitude());
		writer.put3s(centralPoint.getLatitude());
		writer.put1u(tabA.getNumberOfItems());
		writer.put1u(tabB.getNumberOfItems());

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
	 * Inverse of calcTableOffset.
	 */
	private static int calcLowByte(int nodeOffset, int tablesOffset) {
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

	public Area getArea() {
		return area;
	}

	public String reportSizes() {
		int nodesSize = 0;
		for(RouteNode n : nodes)
			nodesSize += n.boundSize();
		return "n=(" + nodes.size() + "," + nodesSize + "), a=" + tabA.size() + ", b=" + tabB.size();
	}
}
