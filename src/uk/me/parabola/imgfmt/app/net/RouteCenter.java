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
	
	private Coord centralPoint;

	private List<RouteNode> nodes = new ArrayList<RouteNode>();

	// These may be pulled into this class
	//private Tables tables = new Tables();
	private TableA tabA = new TableA();
	private TableB tabB = new TableB();
	private TableC tabC = new TableC();

	private int tableAoffset;

	public RouteCenter(Coord cp) {
		this.centralPoint = cp;
	}

	public void addNode(RouteNode node, Coord coord) {
		node.setCoord(centralPoint, coord);
		//node.setLatOff((char) (centralPoint.getLatitude() - coord.getLatitude()));
		//node.setLonOff((char) (centralPoint.getLongitude() - coord.getLongitude()));
		nodes.add(node);
	}
	
	public void write(ImgFileWriter writer) {
		if (nodes.isEmpty())
			return;

		//List<RouteArc>
		for (RouteNode node : nodes) {
			node.write(writer);

			// save table A entries
			for (RouteArc arc : node.arcsIteration()) {
				if (arc.isForward()) {
					tabA.addItem();
				}
			}
		}

		int tmpTabsOff = writer.position();
		int mask = (1 << NODHeader.DEF_ALIGN) - 1;
		tmpTabsOff = (tmpTabsOff + mask) & ~mask;

		// Go back and fill in all the table offsets
		for (RouteNode node : nodes) {
			int pos = node.getOffset();
			log.debug("node pos", pos);
			byte bo = (byte) ((tmpTabsOff - (pos & ~mask)) >> NODHeader.DEF_ALIGN);

			writer.position(pos);
			log.debug("rewrite taba offset", writer.position(), bo);
			writer.put(bo);

			node.writeSecond(writer);
		}

		// Get the position of the tables, and position there.
		int tablesOffset = tmpTabsOff + mask + 1;
		log.debug("write table a at offset", Integer.toHexString(tablesOffset));
		writer.position(tablesOffset);

		// Calculate table A size, this will be filled in later

		// Write the tables header
		writer.put(tabC.getSize());
		writer.put3(centralPoint.getLongitude());
		writer.put3(centralPoint.getLatitude());
		writer.put((byte) tabA.getNumberOfItems());
		writer.put((byte) 0); // number of table B entries

		tableAoffset = writer.position();
		log.debug("tab a offset", tableAoffset);
		tabA.reserve(writer);
		tabB.write(writer);
		tabC.write(writer);
		//tables.write(writer);
		log.info("endof node " + writer.position());
	}

	public void writeTableA(ImgFileWriter writer, RoadNetwork network) {
		writer.position(tableAoffset);
		for (RouteNode node : nodes) {

			// write the table A entries.  Consists of a pointer to net
			// followed by 2 bytes of class and speed flags and road restrictions.
			for (RouteArc arc : node.arcsIteration()) {
				if (arc.isForward()) {
					int pos = arc.getRoadDef().getNetPosition();
					writer.put3(pos);
					writer.put((byte) 0x46);
					writer.put((byte) 0x0);
				}
			}
		}
	}
}
