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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.net.AccessTagsAndBits;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapRoad;

/**
 * Used to remember all the road relevant parameters in a definition which
 * can occur in any order. Also remembers routing nodes and makes sure
 * the generated MapRoads all have the same RoutingNode objects.
 *
 * Use one instance of RoadHelper per file, and reset after reading
 * each road.
 */
class RoadHelper {
	private static final Logger log = Logger.getLogger(RoadHelper.class);

	// routing node store, persistent over resets
	private final Map<Long, CoordNode> nodeCoords = new HashMap<>();

	private int roadId;
	private final List<NodeIndex> nodes = new ArrayList<>();

	private int speed;
	private int roadClass;

	private boolean oneway;
	private boolean toll;

	private byte mkgmapAccess;
	private List<Numbers> numbers;

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
		numbers = null;
	}

	public void setRoadId(int roadId) {
		this.roadId = roadId;
	}

	public void addNode(String value) {
		String[] f = value.split(",");
		nodes.add(new NodeIndex(f));
	}

	/**
	 * @param param cgpsmapper manual:
	 * RouteParam=speed,road_class,one_way,toll,
	 * denied_emergency,denied_delivery,denied_car,denied_bus,denied_taxi,denied_pedestrain,denied_bicycle,denied_truck
	 */
	public void setParam(String param) {
		String[] f = param.split(",");
		speed = Integer.parseInt(f[0]);
		if (speed < 0)
			speed = 0;
		if (speed > 7)
			speed = 7;
		roadClass = Integer.parseInt(f[1]);
		if (roadClass < 0)
			roadClass = 0;
		if (roadClass > 4)
			roadClass = 4;
		oneway = (f.length > 2) ? Integer.parseInt(f[2]) > 0: false;
		toll = (f.length > 3) ? Integer.parseInt(f[3]) > 0: false;
		byte noAccess = 0;
		for (int j = 0; j < f.length - 4; j++){
			if (Integer.parseInt(f[4+j]) == 0)
				continue;
			switch (j){
			case 0: noAccess |= AccessTagsAndBits.EMERGENCY; break; 
			case 1: noAccess |= AccessTagsAndBits.DELIVERY; break; 
			case 2: noAccess |= AccessTagsAndBits.CAR; break; 
			case 3: noAccess |= AccessTagsAndBits.BUS; break; 
			case 4: noAccess |= AccessTagsAndBits.TAXI; break; 
			case 5: noAccess |= AccessTagsAndBits.FOOT; break; 
			case 6: noAccess |= AccessTagsAndBits.BIKE; break; 
			case 7: noAccess |= AccessTagsAndBits.TRUCK; break; 
			}
		}
		mkgmapAccess = (byte) ~noAccess; // we store the allowed vehicles
	}

	public MapRoad makeRoad(MapLine l) {
		assert roadId != 0;

		if (log.isDebugEnabled())
			log.debug("finishing road id " + roadId);

		MapRoad road = new MapRoad(roadId, roadId, l);

		// Set parameters.
		road.setRoadClass(roadClass);
		road.setSpeed(speed);
		if (oneway)
			road.setOneway();
		if (toll)
			road.setToll();
		road.setAccess(mkgmapAccess);

		if (numbers != null && !numbers.isEmpty()) {
			convertNodesForHouseNumbers(road);
			road.setNumbers(numbers);
		}

		List<Coord> points = road.getPoints();

		for (NodeIndex ni : nodes) {
			int n = ni.index;
			if (log.isDebugEnabled())
				log.debug("road has " + points.size() +" points");
			Coord coord = points.get(n);
			long id = coord.getId();
			if (id == 0) {
				CoordNode node = nodeCoords.get((long) ni.nodeId);
				if (node == null) {
					node = new CoordNode(coord, ni.nodeId, ni.boundary, false);
					nodeCoords.put((long) ni.nodeId, node);
				}
				points.set(n, node);
			} else if (id != ni.nodeId) {
				log.warn("Inconsistant node ids");
			}
		}

		return road;
	}

	/**
	 * Make sure that each node that is referenced by the house
	 * numbers is a number node. Some of them will later be changed
	 * to routing nodes.
	 * Only called if numbers is non-null and not empty.
	 */
	private void convertNodesForHouseNumbers(MapRoad road) {
		int rNodNumber = 0;
		for (Numbers n : numbers) {
			int node = n.getNodeNumber();
			n.setIndex(rNodNumber++);
			road.getPoints().get(node).setNumberNode(true);
		}
	}

	public boolean isRoad() {
		return roadId != 0;
	}

	public Map<Long, CoordNode> getNodeCoords() {
		return nodeCoords;
	}

	public void addNumbers(String value) {
		if (numbers == null)
			numbers = new ArrayList<>();
		Numbers num = new Numbers(value);
		if (num.getLeftNumberStyle() != NumberStyle.NONE || num.getRightNumberStyle() != NumberStyle.NONE)
			numbers.add(num);
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
				log.debug("ind=" + index + "node=" + nodeId + "bound=" + boundary);
		}

		public String toString() {
			return String.format("%d,%d,%b", index, nodeId, boundary);
		}
	}
}
