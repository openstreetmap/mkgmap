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
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.imgfmt.app.net.RouteArc;
import uk.me.parabola.imgfmt.app.net.RouteCenter;
import uk.me.parabola.imgfmt.app.net.RouteNode;
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
	
	// NodeId to list of roads that contain it
	private Map<Long, List<MapRoad>> nodeToRoadList = new HashMap<Long, List<MapRoad>>();

	// RoadId to list of nodes in the road
	private Map<Long, List<IndexAndNode>> roadToNodeList = new HashMap<Long, List<IndexAndNode>>();

	private Map<Long, RouteNode> nodes = new HashMap<Long, RouteNode>();

	// save the coordinates.
	private Map<Long, Coord> coords = new HashMap<Long, Coord>();

	private List<MapRoad> mapRoads = new ArrayList<MapRoad>();
	private List<RoadDef> roadDefs = new ArrayList<RoadDef>();

	private List<RouteCenter> centers = new ArrayList<RouteCenter>();


	/**
	 * Add a road to the list of roads that meet at a given node.
	 * @deprecated this is going ...
	 */
	private void addNodeAndRoad(CoordNode node, MapRoad road) {
		mapRoads.add(road);
		roadDefs.add(road.getRoadDef());

		//nodes.put(node.getId(), node);

		addRoadToNode(road, node.getId());

		//List<IndexAndNode> nodeList = roadToNodeList.get(road.getRoadId());
		//if (nodeList == null) {
		//	nodeList = new ArrayList<IndexAndNode>();
		//	roadToNodeList.put(road.getRoadId(), nodeList);
		//}
		//nodeList.add(new IndexAndNode());
	}

	public void addRoad(MapRoad road) {
		mapRoads.add(road);
		roadDefs.add(road.getRoadDef()); //XXX

		CoordNode lastCoord = null;
		int lastIndex = 0;
		//int index = 0;
		List<Coord> coordList = road.getPoints();
		int npoints = coordList.size();
		for (int index = 0; index < npoints; index++) {
			Coord co = coordList.get(index);
			long id = co.getId();
			if (id == 0) 
				continue;

			addRoadToNode(road, id);

			// The next coord determins the heading
			// If this is the not the first node, then create an arc from
			// the previous node to this one (and back again).
			if (lastCoord != null) {
				long lastId = lastCoord.getId();

				RouteNode node1 = getNode(lastId, lastCoord);
				RouteNode node2 = getNode(id, co);

				// Create forward arc from node1 to node2
				Coord bearing = coordList.get(lastIndex + 1);
				RouteArc arc = new RouteArc(road.getRoadDef(), node2, lastCoord, bearing);
				//arc.setHeading(heading);
				arc.setDestinationClass(road.getRoadClass());
				node1.addArc(arc);

				// Create the reverse arc
				bearing = coordList.get(index - 1);
				RouteArc arc2 = new RouteArc(road.getRoadDef(), node1, co, bearing);
				arc2.setDestinationClass(road.getRoadClass());
				arc.setHeading(bearing);
				arc2.setReverse();
				node2.addArc(arc2);

				arc.setOther(arc2);
			} else {
				// This is the first node in the road
				road.getRoadDef().setNode(getNode(id, co));
			}

			lastCoord = (CoordNode) co;
			lastIndex = index;
		}
	}

	private RouteNode getNode(long id, Coord coord) {
		RouteNode node = nodes.get(id);
		if (node == null) {
			node = new RouteNode();
			nodes.put(id, node);
			coords.put(id, coord);
		}
		return node;
	}

	private void addRoadToNode(MapRoad road, long id) {
		List<MapRoad> l = nodeToRoadList.get(id);
		if (l == null) {
			l = new ArrayList<MapRoad>();
			nodeToRoadList.put(id, l);
		}
		l.add(road);
	}

	public List<RoadDef> getRoadDefs() {
		return roadDefs;
	}

	public List<RouteCenter> getCenters() {
		assert !coords.isEmpty();
		Coord center = coords.values().iterator().next(); // XXX pick the first coord as the center...
		log.debug("center is", center.toDegreeString());

		RouteCenter rc = new RouteCenter(center);
		centers.add(rc);

		int localNet = 0;
		for (Map.Entry<Long, RouteNode> ent : nodes.entrySet()) {
			RouteNode node = ent.getValue();

			// Create and add new node to this center
			//RouteNode node = new RouteNode();
			Coord coord = coords.get(ent.getKey());
			rc.addNode(node, coord);

			for (RouteArc arc : node.arcsIteration()) {
				if (arc.isForward())
					arc.setLocalNet(localNet++);
			}
			// Now for each node, there is a list of roads that it leads to
			// we create arc segments for each one.
			//List<MapRoad> roadList = nodeToRoadList.get(ent.getKey());
			//for (MapRoad mr : roadList) {
			//	RoadDef roadDef = mr.getRoadDef();
			//
			//	RouteArc a = new RouteArc(roadDef, node);
			//}
		}

		return centers;
	}

	//public void addRoad(MapRoad road) {
	//}
}
