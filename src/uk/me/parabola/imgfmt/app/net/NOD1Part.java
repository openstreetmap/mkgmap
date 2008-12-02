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

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

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

	private class BBox {
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

	private final BBox bbox = new BBox();

	private final List<RouteNode> nodes = new ArrayList<RouteNode>();
	private final TableA tabA = new TableA();
	private final TableB tabB = new TableB();

	public NOD1Part() {
		log.info("creating new NOD1Part");
	}

	public void addNode(RouteNode node) {
		bbox.extend(node.getCoord());
		nodes.add(node);
		for (RouteArc arc : node.arcsIteration()) {
			tabA.addArc(arc);
			RouteNode dest = arc.getDest();
			if (!bbox.contains(dest.getCoord())) {
				arc.setInternal(false);
				tabB.addNode(dest);
			}
		}
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

		BBox[] split ;
		if (bbox.getWidth() > bbox.getHeight())
			split = bbox.splitLon();
		else
			split = bbox.splitLat();

		NOD1Part[] parts = new NOD1Part[2];

		for (int i = 0; i < split.length; i++)
			parts[i] = new NOD1Part();

		for (RouteNode node : nodes) {
			int i = 0;
			while (!split[i].contains(node.getCoord()))
				i++;
			parts[i].addNode(node);
		}

		for (NOD1Part part : parts)
			centers.addAll(part.subdivide());

		return centers;
	}

	private boolean satisfiesConstraints() {
		log.debug("constraints:", bbox, tabA.size(), tabB.size(), nodes.size());
		return bbox.getMaxDimension() < MAX_SIZE
			&& tabA.size() < MAX_TABA
			&& tabB.size() < MAX_TABB
			&& nodes.size() < MAX_NODES;
	}

	private RouteCenter toRouteCenter() {
		return new RouteCenter(bbox.center(), nodes, tabA, tabB);
	}
}
