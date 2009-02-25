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
 * Create date: 13-Jul-2008
 */
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.NOD1Part;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.net.RouteCenter;
import uk.me.parabola.imgfmt.app.net.RouteNode;
import uk.me.parabola.imgfmt.app.net.RouteRestriction;
import uk.me.parabola.log.Logger;

/**
 * This holds the road network.  That is all the roads and the nodes
 * that connect them together.
 * 
 * @see <a href="http://www.movable-type.co.uk/scripts/latlong.html">Distance / bearing calculations</a>
 * @author Steve Ratcliffe
 */
public class RoadNetwork {
	private static final Logger log = Logger.getLogger(RoadNetwork.class);

	public static final int EMERGENCY = 0;
	public static final int DELIVERY = 1;
	public static final int NO_CAR = 2;
	public static final int NO_BUS = 3;
	public static final int NO_TAXI = 4;
	public static final int NO_FOOT = 5;
	public static final int NO_BIKE = 6;
	public static final int NO_TRUCK = 7;
	public static final int NO_MAX = 8;

	private final Map<Long, RouteNode> nodes = new HashMap<Long, RouteNode>();

	// boundary nodes
	// a node should be in here iff the nodes boundary flag is set
	private final List<RouteNode> boundary = new ArrayList<RouteNode>();
	//private final List<MapRoad> mapRoads = new ArrayList<MapRoad>();

	private final List<RoadDef> roadDefs = new ArrayList<RoadDef>();
	private List<RouteCenter> centers = new ArrayList<RouteCenter>();

	public void addRoad(MapRoad road) {
		//mapRoads.add(road);
		roadDefs.add(road.getRoadDef()); //XXX

		CoordNode lastCoord = null;
		int lastIndex = 0;
		double roadLength = 0;
		double arcLength = 0;

		List<Coord> coordList = road.getPoints();
		int npoints = coordList.size();
		for (int index = 0; index < npoints; index++) {
			Coord co = coordList.get(index);

			if (index > 0) {
				double d = co.distance(coordList.get(index-1));
				arcLength += d;
				roadLength += d;
			}

			long id = co.getId();
			if (id == 0)
				// not a routing node
				continue;

			// The next coord determins the heading
			// If this is the not the first node, then create an arc from
			// the previous node to this one (and back again).
			if (lastCoord != null) {
				long lastId = lastCoord.getId();
				log.debug("lastId = " + lastId + " curId = " + id);
				log.debug("from " + lastCoord.toDegreeString() 
					 + " to " + co.toDegreeString());
				log.debug("arclength=" + arcLength + " roadlength=" + roadLength);

				RouteNode node1 = getNode(lastId, lastCoord);
				RouteNode node2 = getNode(id, co);

				// Create forward arc from node1 to node2
				Coord bearing = coordList.get(lastIndex + 1);
				RouteArc arc = new RouteArc(road.getRoadDef(), node1, node2, bearing, arcLength);
				arc.setForward();
				node1.addArc(arc);

				// Create the reverse arc
				bearing = coordList.get(index - 1);
				RouteArc arc2 = new RouteArc(road.getRoadDef(),
							node2, node1,
							bearing, arcLength);
				node2.addArc(arc2);
			} else {
				// This is the first node in the road
				road.getRoadDef().setNode(getNode(id, co));
			}

			lastCoord = (CoordNode) co;
			lastIndex = index;
			arcLength = 0;
		}
		road.getRoadDef().setLength(roadLength);
	}

	private RouteNode getNode(long id, Coord coord) {
		RouteNode node = nodes.get(id);
		if (node == null) {
			node = new RouteNode(coord);
			nodes.put(id, node);
			if (node.isBoundary())
				boundary.add(node);
		}
		return node;
	}

	public List<RoadDef> getRoadDefs() {
		return roadDefs;
	}

	/**
	 * Split the network into RouteCenters.
	 *
	 * The resulting centers must satisfy several constraints,
	 * documented in NOD1Part.
	 */
	private void splitCenters() {
		if (nodes.isEmpty())
			return;
		assert centers.isEmpty() : "already subdivided into centers";

		NOD1Part nod1 = new NOD1Part();

		for (RouteNode node : nodes.values())
			nod1.addNode(node);
		centers = nod1.subdivide();
	}

	public List<RouteCenter> getCenters() {
		if (centers.isEmpty())
			splitCenters();
		return centers;
	}

	/**
	 * Get the list of nodes on the boundary of the network.
	 *
	 * Currently empty.
	 */
	public List<RouteNode> getBoundary() {
		return boundary;
	}

	public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode) {
		RouteNode fn = nodes.get(fromNode.getId());
		RouteNode tn = nodes.get(toNode.getId());
		RouteNode vn = nodes.get(viaNode.getId());

		assert fn != null : "can't locate 'from' RouteNode with id " + fromNode.getId();
		assert tn != null : "can't locate 'to' RouteNode with id " + toNode.getId();
		assert vn != null : "can't locate 'via' RouteNode with id " + viaNode.getId();

		RouteArc fa = vn.getArcTo(fn); // inverse arc gets used
		RouteArc ta = vn.getArcTo(tn);

		assert fa != null : "can't locate arc from 'via' node to 'from' node";
		assert ta != null : "can't locate arc from 'via' node to 'to' node";

		vn.addRestriction(new RouteRestriction(fa, ta));
    }

}
