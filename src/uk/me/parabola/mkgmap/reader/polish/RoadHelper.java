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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.RoadNetwork;

/**
 * Used to remember all the road relavent parameters in a definition which
 * can occur in any order. Also remembers routing nodes and makes sure
 * the generated MapRoads all have the same RoutingNode objects.
 *
 * Use one instance of RoadHelper per file, and reset after reading
 * each road.
 */
class RoadHelper {
	private static final Logger log = Logger.getLogger(RoadHelper.class);

	// routing node store, persistent over resets
	private final Map<Long, CoordNode> nodeCoords = new HashMap<Long, CoordNode>();

	private int roadId;
	private final Map<Integer,NodeIndex> nodes = new HashMap<Integer,NodeIndex>();

	private int speed;
	private int roadClass;

	private boolean oneway;
	private boolean toll;

	private boolean[] access;

	public RoadHelper() {
		clear();
	}

	public void clear() {
		roadId = 0;
		nodes.clear();

		speed = 0;
		roadClass = 0;
		oneway = false;
		toll = false;
		access = new boolean[RoadNetwork.NO_MAX];
	}

	public void setRoadId(int roadId) {
		this.roadId = roadId;
	}

	public void addNode(int nodeIndex, String value) {
		if (nodes.containsKey(nodeIndex))
			log.warn("duplicate nodeidx %d, overwriting", nodeIndex);
		String[] f = value.split(",");
		nodes.put(nodeIndex, new NodeIndex(f));
	}

	public void setParam(String param) {
		String[] f = param.split(",");
		speed = Integer.parseInt(f[0]);
		roadClass = Integer.parseInt(f[1]);
		oneway = Integer.parseInt(f[2]) > 0;
		toll = Integer.parseInt(f[3]) > 0;
		for (int j = 0; j < RoadNetwork.NO_MAX; j++)
			access[j] = Integer.parseInt(f[4+j]) > 0;
	}

	public MapRoad makeRoad(MapLine l) {
		assert roadId != 0;

		if (log.isDebugEnabled())
			log.debug("finishing road id " + roadId);

		MapRoad road = new MapRoad(roadId, l);

		// Set parameters.
		road.setRoadClass(roadClass);
		road.setSpeed(speed);
		if (oneway)
			road.setOneway();
		if (toll)
			road.setToll();
		road.setAccess(access);

		List<Coord> points = road.getPoints();

		road.setNumNodes(nodes.size());
		boolean starts = false;
		boolean intern = false;
		for (NodeIndex ni : nodes.values()) {
			int n = ni.index;
			if (n == 0)
				starts = true;
			else if (n < points.size() - 1)
				intern = true;
			if (log.isDebugEnabled())
				log.debug("road has " + points.size() +" points");
			Coord coord = points.get(n);
			long id = coord.getId();
			if (id == 0) {
				CoordNode node = nodeCoords.get((long) ni.nodeId);
				if (node == null) {
					node = new CoordNode(coord.getLatitude(), coord.getLongitude(), ni.nodeId, ni.boundary);
					nodeCoords.put((long) ni.nodeId, node);
				}
				points.set(n, node);
			} else if (id != ni.nodeId) {
				log.warn("Inconsistant node ids");
			}
		}
		road.setStartsWithNode(starts);
		road.setInternalNodes(intern);
		return road;
	}

	public boolean isRoad() {
		return roadId != 0;
	}

	private static class NodeIndex {
		private final int index;
		private final int nodeId;
		private boolean boundary;

		private NodeIndex(String[] f) {
			// f[0] is the index into the line
			// f[1] is the node id
			// f[2] is whether it's a boundary node
			index = Integer.parseInt(f[0]);
			nodeId = Integer.parseInt(f[1]);
			if (f.length > 2)
				boundary = Integer.parseInt(f[2]) > 0;
			if (log.isDebugEnabled())
				log.debug("ind=%d, node=%d, bound=%b\n", index, nodeId, boundary);
		}
	}
}
