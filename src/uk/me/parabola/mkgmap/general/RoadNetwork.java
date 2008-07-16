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
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * This holds the road network.  That is all the roads and the nodes
 * that connect them together.
 * 
 * @author Steve Ratcliffe
 */
public class RoadNetwork {
	// NodeId to list of roads that contain it
	private Map<Long, List<MapRoad>> roads = new HashMap<Long, List<MapRoad>>();

	// RoadId to list of nodes in the road
	private Map<Long, List<IndexAndNode>> nodes = new HashMap<Long, List<IndexAndNode>>();

	/**
	 * Add a node to a road.
	 */
	public void addNodeAndRoad(long nodeId, MapRoad road) {
		List<MapRoad> list = roads.get(nodeId);
		if (list == null) {
			list = new ArrayList<MapRoad>();
			roads.put(nodeId, list);
		}

		List<IndexAndNode> nodeList = nodes.get(road.getRoadId());
		if (nodeList == null) {
			nodeList = new ArrayList<IndexAndNode>();
			nodes.put(road.getRoadId(), nodeList);
		}
	}

	public void addRoad(MapRoad road) {

		//for (Coord co : road.getPoints()) {
		//	long id = co.getId();
		//	if (id != 0) {
		//
		//		List<MapRoad> l = roads.get(id);
		//		if (l == null) {
		//			l = new ArrayList<MapRoad>();
		//			roads.put(id, l);
		//		}
		//		RoadDef roadDef = new RoadDef();
		//		//roadDef.addPolylineRef();
		//		l.add(roadDef);
		//	}
		//}
	}
}
