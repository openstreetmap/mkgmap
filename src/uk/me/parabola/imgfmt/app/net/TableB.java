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
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Table B contains offsets in NOD1 of neighbouring nodes
 * outside the containing RouteCenter.
 */
public class TableB {

	private final ArrayList<RouteNode> nodes = new ArrayList<RouteNode>();

	private final static int ITEM_SIZE = 3;

	private int offset;

        /**
         * Retrieve the size of the Table as an int.
         *
         * While Table B is limited in size (0x100 entries),
         * we temporarily build larger tables while subdividing
         * the network.
         */
	public int size() {
		return nodes.size();
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
		assert nodes.size() < 0x100 : "Table B too large.";
		return (byte) nodes.size();
	}

	/**
	 * Add a node (in another RouteCenter) to this Table and return its index.
	 *
	 * This index may overflow while it isn't certain that the
	 * table fulfills the size constraint.
	 */
	public void addNode(RouteNode node) {
		int i = nodes.indexOf(node);
		if (i < 0) {
			//i = nodes.size();
			nodes.add(node);
		}
	}

	/**
	 * Retrieve a nodes index. Checked for correct bounds.
	 */
	public byte getIndex(RouteNode node) {
		int i = nodes.indexOf(node);
		assert i >= 0 : "Trying to read Table B index for non-registered node.";
		assert i < 0x100 : "Table B index too large.";
		return (byte) i;
	}

	/**
	 * Reserve space, since node offsets in other
	 * RoutingCenters need not be known yet. See writePost.
	 */
	public void write(ImgFileWriter writer) {
		offset = writer.position();
		int size = nodes.size() * ITEM_SIZE;
		for (int i = 0; i < size; i++)
			writer.put((byte) 0);
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
