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

import java.util.ArrayList;

import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * @author Steve Ratcliffe
 */
public class TableA {
	private static final Logger log = Logger.getLogger(TableA.class);

	private static final int ITEM_SIZE = 5;

	private int offset;

	private ArrayList<Arc> arcs = new ArrayList<Arc>();

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

		boolean equals(Arc arc) {
			return first.equals(arc.first)
				&& second.equals(arc.second)
				&& roadDef.equals(arc.roadDef);
		}
	}

	/**
	 * Add an arc to the table if not present and set its index.
	 */
	public void addArc(RouteArc arc) {
		assert arc.isInternal():
			"no table A entries for external arcs.";
		Arc narc = new Arc(arc);
		int i;
		if ((i = arcs.indexOf(narc)) < 0) {
			i = arcs.size();
			assert i < 0x100 : "too many entries in Table A";
			arcs.add(narc);
		}
		arc.setIndexA((byte) i);
	}

	public byte getNumberOfItems() {
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

	public void writePost(ImgFileWriter writer) {
		writer.position(offset);
		for (Arc arc : arcs) {
			// write the table A entries.  Consists of a pointer to net
			// followed by 2 bytes of class and speed flags and road restrictions.
			int pos = arc.roadDef.getNetPosition();
			writer.put3(pos);
			writer.put((byte) 0x46);
			writer.put((byte) 0x0);
		}
	}
}
