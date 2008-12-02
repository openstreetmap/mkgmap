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
 * Author: Robert Vollmert
 * Create date: 02-Dec-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.net.RouteCenter;
import uk.me.parabola.imgfmt.app.net.RouteNode;
import uk.me.parabola.imgfmt.app.net.TableA;
import uk.me.parabola.imgfmt.app.net.TableB;
import uk.me.parabola.log.Logger;

/**
 * This is a component of the RoadNetwork.
 *
 * Keeps track of outside neighbours and allows subdivision
 * to satisfy NOD1 constraints. 
 *
 * The approach to subdivision is to tile the map into RouteCenters. 
 * One could imagine that overlapping RouteCenters would be an option,
 * say by splitting largely independent networks (motorways, footways).
 *
 * Could be rolled into RouteCenter.
 */
public class NOD1Part {
	private static final Logger log = Logger.getLogger(NOD1Part.class);

	// maximal width and height of the bounding box, since
	// NOD 1 coordinate offsets are at most 16 bit wide.
	// (halve to be on the safe side)
	private static final int MAX_SIZE_UNSAFE = 1 << 16;
	private static final int MAX_SIZE = MAX_SIZE_UNSAFE / 2;

	// Table A has at most 0x100 entries
	private static final int MAX_TABA_UNSAFE = 0x100;
	private static final int MAX_TABA = MAX_TABA_UNSAFE / 2;

	// Table B has at most 0x40 entries
	private static final int MAX_TABB_UNSAFE = 0x40;
	private static final int MAX_TABB = MAX_TABB_UNSAFE / 2;

	// todo: something less arbitrary
	private static final int MAX_NODES = 0x30;

	private final Area bbox;

	private final List<RouteNode> nodes = new ArrayList<RouteNode>();
	private final TableA tabA = new TableA();
	private final TableB tabB = new TableB();

	public NOD1Part(Area bbox) {
		this.bbox = bbox;
	}

	public void addNode(RouteNode node) {
		nodes.add(node);
		for (RouteArc arc : node.arcsIteration()) {
			tabA.addArc(arc);
			RouteNode dest = arc.getDest();
			// XXX: check bbox borders in bbox.contains()
			if (!bbox.contains(dest.getCoord())) {
				arc.setInternal(false);
				tabB.addNode(dest);
			}
		}
	}

	/**
	 * Subdivide this part recursively until it satisfies the constraints.
	 */
	private List<RouteCenter> subdivide() {
		List<RouteCenter> centers = new LinkedList<RouteCenter>();

		if (satisfiesConstraints()) {
			centers.add(this.toRouteCenter());
			return centers;
		}

		int xsplit = 1, ysplit = 1;

		if (bbox.getWidth() > bbox.getHeight())
			xsplit = 2;
		else
			ysplit = 2;

		Area[] areas = bbox.split(xsplit, ysplit);
		NOD1Part[] parts = new NOD1Part[areas.length];

		for (int i = 0; i < areas.length; i++)
			parts[i] = new NOD1Part(areas[i]);

		for (RouteNode node : nodes) {
			int i = 0;
			while (!areas[i].contains(node.getCoord()))
				i++;
			parts[i].addNode(node);
		}

		for (NOD1Part part : parts)
			centers.addAll(part.subdivide());

		return centers;
	}

	private boolean satisfiesConstraints() {
		return bbox.getMaxDimention() < MAX_SIZE
			&& tabA.size() < MAX_TABA
			&& tabB.size() < MAX_TABB
			&& nodes.size() < MAX_NODES;
	}

	private RouteCenter toRouteCenter() {
		return new RouteCenter(bbox.getCenter(), nodes, tabA, tabB);
	}
}
