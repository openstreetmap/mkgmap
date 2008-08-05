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
 * Create date: 04-Aug-2008
 */
package uk.me.parabola.mkgmap.reader.polish;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.RoadNetwork;

/**
 * Used to remember all the road relavent parameters in a definition which
 * can occur in any order.
 */
class RoadHelper {
	private boolean hasRoads;
	private int roadId;
	private int lastNodeIndex;
	private List<NodeIndex> nodes = new ArrayList<NodeIndex>();
	private Map<Long, CoordNode> nodeCoords = new HashMap<Long, CoordNode>();

	private RoadNetwork roadNetwork = new RoadNetwork();
	private MapRoad road;
	private int speed;
	private int roadClass;

	public void clear() {
		roadId = 0;
		lastNodeIndex = 0;
		nodes.clear();
		road = null;
	}

	public void setRoadId(int roadId) {
		this.roadId = roadId;
		hasRoads = true;
	}

	public void addNode(int nodeIndex, String value) {
		if (nodeIndex < lastNodeIndex)
			return;
		lastNodeIndex = nodeIndex;
		String[] f = value.split(",");
		// f[0] is the index into the line
		// f[1] is the node id
		nodes.add(new NodeIndex(f[0], f[1]));
	}

	public void addLine(MapLine l) {
		if (roadId == 0)
			return;

		road = new MapRoad(roadId, l);
	}

	public void setParam(String param) {
		//this.param = param;
		String[] f = param.split(",");
		speed = Integer.parseInt(f[0]);
		roadClass = Integer.parseInt(f[1]);
	}

	public void finishRoad() {
		if (roadId == 0)
			return;

		System.out.printf("Road id %d\n", roadId);

		// Set class and speed
		road.setRoadClass(roadClass);
		road.setSpeed(speed);

		for (NodeIndex ni : nodes) {
			int n = ni.index;
			List<Coord> points = road.getPoints();
			Coord coord = points.get(n);
			long id = coord.getId();
			if (id == 0) {
				CoordNode node = nodeCoords.get((long) ni.nodeId);
				if (node == null) {
					node = new CoordNode(coord.getLatitude(), coord.getLongitude(), ni.nodeId);
					nodeCoords.put((long) ni.nodeId, node);
				}
				points.set(n, node);
				//roadNetwork.addNodeAndRoad(node, road);
			} else if (id != ni.nodeId) {
				System.out.println("Inconsistant node ids");
			}
		}

		roadNetwork.addRoad(road);
	}

	public RoadNetwork getRoadNetwork() {
		return hasRoads ? roadNetwork : null;
	}

	public boolean isRoad() {
		return roadId != 0;
	}

	public MapRoad getRoad() {
		return road;
	}

	private static class NodeIndex {
		private int index;
		private int nodeId;

		private NodeIndex(String sInd, String sNode) {
			index = Integer.parseInt(sInd);
			nodeId = Integer.parseInt(sNode);
			System.out.printf("ind=%d, node=%d\n", index, nodeId);
		}
	}
}
