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

	private Map<Long, RouteNode> nodes = new HashMap<Long, RouteNode>();

	// save the coordinates.
	private Map<Long, Coord> coords = new HashMap<Long, Coord>();

	private List<MapRoad> mapRoads = new ArrayList<MapRoad>();
	private List<RoadDef> roadDefs = new ArrayList<RoadDef>();

	private List<RouteCenter> centers = new ArrayList<RouteCenter>();


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
			if (id == 0) {
				log.debug("got id 0");
				continue;
			}

			addRoadToNode(road, id);

			// The next coord determins the heading
			// If this is the not the first node, then create an arc from
			// the previous node to this one (and back again).
			if (lastCoord != null) {
				long lastId = lastCoord.getId();
				log.debug("lastId = " + lastId);

				RouteNode node1 = getNode(lastId, lastCoord);
				RouteNode node2 = getNode(id, co);

				// Create forward arc from node1 to node2
				Coord bearing = coordList.get(lastIndex + 1);
				RouteArc arc = new RouteArc(road.getRoadDef(), node2, lastCoord, bearing);
				//arc.setHeading(heading);
				arc.setForward();
				arc.setDestinationClass(road.getRoadClass());
				node1.addArc(arc);

				// Create the reverse arc
				bearing = coordList.get(index - 1);
				RouteArc arc2 = new RouteArc(road.getRoadDef(), node1, co, bearing);
				arc2.setDestinationClass(road.getRoadClass());
				arc.setHeading(bearing);
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

		RouteCenter rc = null;

		int localNet = 0;
		for (Map.Entry<Long, RouteNode> ent : nodes.entrySet()) {
			Coord coord = coords.get(ent.getKey());
			RouteNode node = ent.getValue();

			if (rc == null || !rc.nodeFits(node)) {
				rc = new RouteCenter(coord);
				log.debug("new route center at", coord.toDegreeString());
				centers.add(rc);
			}

			rc.addNode(node, coord);

			for (RouteArc arc : node.arcsIteration()) {
				if (arc.isForward())
					arc.setLocalNet(localNet++);
			}
		}

		return centers;
	}
}
