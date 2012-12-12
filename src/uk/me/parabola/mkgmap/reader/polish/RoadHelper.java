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
import java.util.ListIterator;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
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

	private static final int NUM_ACCESS = 8;

	// routing node store, persistent over resets
	private final Map<Long, CoordNode> nodeCoords = new HashMap<Long, CoordNode>();

	// Next node number to use for nodes constructed for house numbers. Persists over reset.
	private long houseNumberNodeNumber = 16000000;

	private int roadId;
	private final List<NodeIndex> nodes = new ArrayList<NodeIndex>();

	private int speed;
	private int roadClass;

	private boolean oneway;
	private boolean toll;

	private boolean[] access;
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
		access = new boolean[NUM_ACCESS];
		numbers = null;
	}

	public void setRoadId(int roadId) {
		this.roadId = roadId;
	}

	public void addNode(String value) {
		String[] f = value.split(",");
		nodes.add(new NodeIndex(f));
	}

	public void setParam(String param) {
		String[] f = param.split(",");
		speed = Integer.parseInt(f[0]);
		roadClass = Integer.parseInt(f[1]);
		oneway = Integer.parseInt(f[2]) > 0;
		toll = Integer.parseInt(f[3]) > 0;
		for (int j = 0; j < f.length - 4; j++)
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

		if (numbers != null && !numbers.isEmpty()) {
			convertNodesForHouseNumbers();
			road.setNumbers(numbers);
		}

		List<Coord> points = road.getPoints();
		road.setNumNodes(nodes.size());

		boolean starts = false;
		boolean intern = false;
		for (NodeIndex ni : nodes) {
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

	/**
	 * Convert the node index into a routing node number.
	 *
	 * If necessary a new routing node is created, if there is not one already
	 * These constructed routing nodes are not connected to any other road and so
	 * should be marked as such in the NOD2 bit stream, but we don't appear to do that yet.
	 *
	 * Only called if numbers is non-null and not empty.
	 */
	private void convertNodesForHouseNumbers() {
		for (Numbers n : numbers) {
			int node = n.getNodeNumber();

			// This assumes that the nodes are sorted by index.
			ListIterator<NodeIndex> iterator = nodes.listIterator();
			while (iterator.hasNext()) {
				NodeIndex ni = iterator.next();
				if (ni.index == node) {
					// It was already there (a common case)
					n.setRnodNumber(iterator.previousIndex());
					break;
				} else if (ni.index > node) {
					// there is no routing node for this node index, need to insert one.
					break;
				}
			}

			// If we don't have a routing node number then we have to construct one.
			if (!n.hasRnodNumber()) {
				NodeIndex hnNode = new NodeIndex(new String[] {
						String.valueOf(node),
						String.valueOf(houseNumberNodeNumber++),
						"0"
				});

				iterator.previous();
				iterator.add(hnNode);
				n.setRnodNumber(iterator.previousIndex());
				//System.out.printf("ADDING RN on %d, hn=%s, rn=%d\n", roadId, hnNode, n.getRnodNumber());
			}
		}

		// Sanity checking. TODO remove
		//int lastInd = -1;
		//for (NodeIndex n : nodes) {
		//	assert n.index > lastInd;
		//	lastInd = n.index;
		//
		//}
		//System.out.println("start");
		//Numbers num = null;
		//for (Numbers n1 : numbers) {
		//	int ncount = 0;
		//	for (NodeIndex n : nodes) {
		//		System.out.printf("n1.node=%d, ni=%s, ni.index=%d\n", n1.getNodeNumber(), n, n.index);
		//		if (n1.getNodeNumber() == n.index) {
		//			num = n1;
		//			break;
		//		}
		//		ncount++;
		//	}
		//	assert num != null && num.getRnodNumber() == ncount;
		//}
	}

	public boolean isRoad() {
		return roadId != 0;
	}

	public Map<Long, CoordNode> getNodeCoords() {
		return nodeCoords;
	}

	public void addNumbers(String value) {
		if (numbers == null)
			numbers = new ArrayList<Numbers>();
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
				log.debug("ind=%d, node=%d, bound=%b\n", index, nodeId, boundary);
		}

		public String toString() {
			return String.format("%d,%d,%b", index, nodeId, boundary);
		}
	}
}
