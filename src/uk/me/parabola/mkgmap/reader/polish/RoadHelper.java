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

	private boolean dirIndicator;

	private int speed;
	private int roadClass;

	private boolean oneway;
	private boolean toll;

	public static final int EMERGENCY = 0;
	public static final int DELIVERY = 1;
	public static final int NO_CAR = 2;
	public static final int NO_BUS = 3;
	public static final int NO_TAXI = 4;
	public static final int NO_FOOT = 5;
	public static final int NO_BIKE = 6;
	public static final int NO_TRUCK = 7;
	public static final int NO_MAX = 8;

	private boolean[] access;

	public RoadHelper() {
		clear();
	}

	public void clear() {
		roadId = 0;
		nodes.clear();

		dirIndicator = false;
		speed = 0;
		roadClass = 0;
		oneway = false;
		toll = false;
		access = new boolean[NO_MAX];
	}

	public void setRoadId(int roadId) {
		this.roadId = roadId;
	}

	public void addNode(int nodeIndex, String value) {
		if (nodes.containsKey(nodeIndex))
			log.warn("duplicate nodeidx %d, overwriting", nodeIndex);
		String[] f = value.split(",");
		// f[0] is the index into the line
		// f[1] is the node id
		// f[2] is whether it's a boundary node (?)
		nodes.put(nodeIndex, new NodeIndex(f[0], f[1], f[2]));
	}

	public void setDirIndicator(boolean dir) {
		this.dirIndicator = dir;
	}

	public void setParam(String param) {
		String[] f = param.split(",");
		speed = Integer.parseInt(f[0]);
		roadClass = Integer.parseInt(f[1]);
		oneway = Integer.parseInt(f[2]) > 0;
		toll = Integer.parseInt(f[3]) > 0;
		for (int j = 0; j < NO_MAX; j++)
			access[j] = Integer.parseInt(f[4+j]) > 0;
	}

	public MapRoad makeRoad(MapLine l) {
		assert roadId != 0;

		log.debug("finishing road id " + roadId);

		MapRoad road = new MapRoad(roadId, l);

		// Set parameters.
		road.setDirIndicator(dirIndicator);
		road.setRoadClass(roadClass);
		road.setSpeed(speed);
		road.setOneway(oneway);
		road.setToll(toll);
		road.setAccess(access);

		List<Coord> points = road.getPoints();

		road.setNumNodes(nodes.size());
		boolean starts = false;
		boolean intern = false;
		for (NodeIndex ni : nodes.values()) {
			int n = ni.index;
			if (n == 0)
				starts = true;
			else if (n < nodes.size())
				intern = true;
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
		private int index;
		private int nodeId;
		private boolean boundary;

		private NodeIndex(String sInd, String sNode, String sBound) {
			index = Integer.parseInt(sInd);
			nodeId = Integer.parseInt(sNode);
			boundary = Integer.parseInt(sBound) > 0;
			log.debug("ind=%d, node=%d, bound=%b\n", index, nodeId, boundary);
		}
	}
}
