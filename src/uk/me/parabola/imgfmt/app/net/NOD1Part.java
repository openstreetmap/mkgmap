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
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
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

	/*
	 * Constraints:
	 *
         * 1. Nodes section smaller than about 0x4000, which gives
         *    a bound on the number of nodes.  
         * 2. At most 0x100 entries in Table A. This gives a bound
         *    on the number of (forward) arcs meeting this
         *    RouteCenter.
         * 3. At most 0x40 entries in Table B. This gives a bound
         *    on the number of neighboring nodes.
         * 4. Absolute values of coordinate offsets at most 0x8000,
         *    which translates to about 0.7 degrees, so bounding
         *    box should be at most 1.4 x 1.4 degrees assuming
         *    the reference is in the middle. (With small offsets,
         *    this would be 0.08 x 0.08 degrees.)
         * 5. Absolute values of relative NOD1 offsets at most
         *    0x2000, which limits the nodes section to 0x2000
         *    unless we take care to order the nodes nicely.
	 * 6. Distance between nodes and start of tables must
	 *    fit in a char for writing Table C. So nodes
	 *    section smaller than 0x10000.
         */

	// maximal width and height of the bounding box, since
	// NOD 1 coordinate offsets are at most 16 bit wide.
	private static final int MAX_SIZE_UNSAFE = 1 << 16;
//	private static final int MAX_SIZE = MAX_SIZE_UNSAFE / 2;
	private static final int MAX_SIZE = MAX_SIZE_UNSAFE - 0x800;

	// Table A has at most 0x100 entries
	private static final int MAX_TABA_UNSAFE = 0x100;
//	private static final int MAX_TABA = MAX_TABA_UNSAFE / 2;
	private static final int MAX_TABA = MAX_TABA_UNSAFE - 0x8;

	// Table B has at most 0x40 entries
	private static final int MAX_TABB_UNSAFE = 0x40;
//	private static final int MAX_TABB = MAX_TABB_UNSAFE / 2;
	private static final int MAX_TABB = MAX_TABB_UNSAFE - 0x2;

	// Nodes size is bounded due to the byte offset to Tables.
	private static final int MAX_NODES_SIZE = (1 << NODHeader.DEF_ALIGN) * 0x30;
	private int nodesSize;

	public class BBox {
		int maxLat, minLat, maxLon, minLon;
		boolean empty;

		BBox() {
			empty = true;
		}

		BBox(Coord co) {
			empty = false;
			int lat = co.getLatitude();
			int lon = co.getLongitude();
			minLat = lat;
			maxLat = lat+1;
			minLon = lon;
			maxLon = lon+1;
		}

		BBox(int minLat, int maxLat, int minLon, int maxLon) {
			empty = false;
			this.minLat = minLat;
			this.maxLat = maxLat;
			this.minLon = minLon;
			this.maxLon = maxLon;
		}

		boolean contains(BBox bbox) {
			return minLat <= bbox.minLat && bbox.maxLat <= maxLat
				&& minLon <= bbox.minLon && bbox.maxLon <= maxLon;
		}
	
		boolean contains(Coord co) {
			return contains(new BBox(co));
		}

		void extend(BBox bbox) {
			if (bbox.empty)
				return;
			if (empty) {
				empty = false;
				minLat = bbox.minLat;
				maxLat = bbox.maxLat;
				minLon = bbox.minLon;
				maxLon = bbox.maxLon;
			} else {
				minLat = Math.min(minLat, bbox.minLat);
				maxLat = Math.max(maxLat, bbox.maxLat);
				minLon = Math.min(minLon, bbox.minLon);
				maxLon = Math.max(maxLon, bbox.maxLon);
			}
		}

		void extend(Coord co) {
			extend(new BBox(co));
		}

		Coord center() {
			assert !empty : "trying to get center of empty BBox";
			return new Coord((minLat + maxLat)/2, (minLon + maxLon)/2);
		}

		BBox[] splitLat() {
			BBox[] ret = new BBox[2];
			int midLat = (minLat + maxLat) / 2;
			ret[0] = new BBox(minLat, midLat, minLon, maxLon);
			ret[1] = new BBox(midLat, maxLat, minLon, maxLon);
			return ret;
		}

		BBox[] splitLon() {
			BBox[] ret = new BBox[2];
			int midLon = (minLon + maxLon) / 2;
			ret[0] = new BBox(minLat, maxLat, minLon, midLon);
			ret[1] = new BBox(minLat, maxLat, midLon, maxLon);
			return ret;
		}

		int getWidth() {
			return maxLon - minLon;
		}

		int getHeight() {
			return maxLat - minLat;
		}

		int getMaxDimension() {
			return Math.max(getWidth(), getHeight());
		}

		public String toString() {
			return "BBox[" + new Coord(minLat,minLon).toDegreeString()
				+ ", " + new Coord(maxLat,maxLon).toDegreeString() + "]";
		}
	}

	// The area we are supposed to cover.
	private final BBox bbox;
	// The area that actually has nodes.
	private final BBox bboxActual = new BBox();

	private final List<RouteNode> nodes = new ArrayList<RouteNode>();
	private final TableA tabA = new TableA();
	private final TableB tabB = new TableB();

	/**
	 * Create an unbounded NOD1Part.
	 *
	 * All nodes will be accepted by addNode and
	 * all arcs will be considered internal.
	 */
	public NOD1Part() {
		log.info("creating new unbounded NOD1Part");
		this.bbox = null;
	}

	/**
	 * Create a bounded NOD1Part.
	 *
	 * The bounding box is used to decide which arcs
	 * are internal.
	 */
	private NOD1Part(BBox bbox) {
		log.info("creating new NOD1Part:", bbox);
		this.bbox = bbox;
	}

	/**
	 * Add a node to this part.
	 *
	 * The node is used to populate the tables. If an
	 * arc points outside the bbox, we know it's not
	 * an internal arc. It might still turn into an
	 * external arc at a deeper level of recursion.
	 */
	public void addNode(RouteNode node) {
		assert bbox == null || bbox.contains(node.getCoord())
			: "trying to add out-of-bounds node: " + node;

		bboxActual.extend(node.getCoord());
		nodes.add(node);
		for (RouteArc arc : node.arcsIteration()) {
			tabA.addArc(arc);
			RouteNode dest = arc.getDest();
			if (bbox != null && !bbox.contains(dest.getCoord())) {
				arc.setInternal(false);
				tabB.addNode(dest);
			}
		}
		nodesSize += node.boundSize();
	}

	/**
	 * Subdivide this part recursively until it satisfies the constraints.
	 */
	public List<RouteCenter> subdivide() {
		List<RouteCenter> centers = new LinkedList<RouteCenter>();

		if (satisfiesConstraints()) {
			centers.add(this.toRouteCenter());
			return centers;
		}

		log.info("subdividing", bbox, bboxActual);
		BBox[] split ;
		if (bboxActual.getWidth() > bboxActual.getHeight())
			split = bboxActual.splitLon();
		else
			split = bboxActual.splitLat();

		NOD1Part[] parts = new NOD1Part[2];

		for (int i = 0; i < split.length; i++)
			parts[i] = new NOD1Part(split[i]);

		for (RouteNode node : nodes) {
			int i = 0;
			while (!split[i].contains(node.getCoord()))
				i++;
			parts[i].addNode(node);
		}

		for (NOD1Part part : parts) {
			if(part.nodes.size() == nodes.size()) {
				log.error("Subdivision failed to reduce number of nodes in " + bbox + " (giving up, sorry)");
				System.exit(1);
			}
			else if(!part.bboxActual.empty)
				centers.addAll(part.subdivide());
		}

		return centers;
	}

	private boolean satisfiesConstraints() {
		log.debug("constraints:", bboxActual, tabA.size(), tabB.size(), nodesSize);
		return bboxActual.getMaxDimension() < MAX_SIZE
			&& tabA.size() < MAX_TABA
			&& tabB.size() < MAX_TABB
			&& nodesSize < MAX_NODES_SIZE;
	}

	/**
	 * Convert to a RouteCenter.
	 *
	 * satisfiesConstraints() should be true for this to
	 * be a legal RouteCenter.
	 */
	private RouteCenter toRouteCenter() {
		return new RouteCenter(bboxActual.center(), nodes, tabA, tabB);
	}
}
