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
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * @author Steve Ratcliffe
 */
public class TableB {

	private final ArrayList<RouteNode> nodes = new ArrayList<RouteNode>();

	private final static int ITEM_SIZE = 3;

	private int offset;

	public byte getSize() {
		return (byte) nodes.size();
	}

	/**
	 * Add a node (in another RouteCenter) to this Table and return its index.
	 */
	public byte addNode(RouteNode node) {
		int i = nodes.indexOf(node);
		if (i < 0) {
			i = nodes.size();
			assert i < 0x40 : "Table B too large.";
			nodes.add(node);
		}
		return (byte) i;
	}

	/**
	 * Reserve space, since node offsets in other
	 * RoutingCenters need not be known yet. See writePost.
	 */
	public void write(ImgFileWriter writer) {
		offset = writer.position();
		int size = nodes.size() * ITEM_SIZE;
		writer.position(offset + size);
	}

	/**
	 * Fill in node offsets.
	 */
	public void writePost(ImgFileWriter writer) {
		writer.position(offset);
		for (RouteNode node : nodes)
			writer.put3(node.getOffsetNod1());
	}
}
