/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.AreaClipper;
import uk.me.parabola.mkgmap.general.Clipper;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Convert from OSM to the mkgmap intermediate format using a style.
 * A style is a collection of files that describe the mappings to be used
 * when converting.
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(StyledConverter.class);

	private final String[] nameTagList;

	private final MapCollector collector;

	private Clipper clipper = Clipper.NULL_CLIPPER;

	private int roadId;

	private final int MAX_NODES_IN_WAY = 16;

	// nodeIdMap maps a Coord into a nodeId
	private final Map<Coord, Integer> nodeIdMap = new HashMap<Coord, Integer>();
	private int nextNodeId;
	
	private final Rule wayRules;
	private final Rule nodeRules;
	private final Rule relationRules;

	private LineAdder lineAdder = new LineAdder() {
		public void add(MapLine element) {
			if (element instanceof MapRoad)
				collector.addRoad((MapRoad) element);
			else
				collector.addLine(element);
		}
	};

	public StyledConverter(Style style, MapCollector collector) {
		this.collector = collector;

		nameTagList = style.getNameTagList();
		wayRules = style.getWayRules();
		nodeRules = style.getNodeRules();
		relationRules = style.getRelationRules();

		LineAdder overlayAdder = style.getOverlays(lineAdder);
		if (overlayAdder != null)
			lineAdder = overlayAdder;
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		if (way.getPoints().size() < 2)
			return;

		preConvertRules(way);

		GType foundType = wayRules.resolveType(way);
		if (foundType == null)
			return;

		postConvertRules(way, foundType);

		if (foundType.getFeatureKind() == GType.POLYLINE) {
		    if(foundType.isRoad())
			addRoad(way, foundType);
		    else
			addLine(way, foundType);
		}
		else
			addShape(way, foundType);
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		preConvertRules(node);

		GType foundType = nodeRules.resolveType(node);
		if (foundType == null)
			return;

		// If the node does not have a name, then set the name from this
		// type rule.
		log.debug("node name", node.getName());
		if (node.getName() == null) {
			node.setName(foundType.getDefaultName());
			log.debug("after set", node.getName());
		}

		postConvertRules(node, foundType);

		addPoint(node, foundType);
	}

	/**
	 * Rules to run before converting the element.
	 */
	private void preConvertRules(Element el) {
		if (nameTagList == null)
			return;

		for (String t : nameTagList) {
			String val = el.getTag(t);
			if (val != null) {
				el.addTag("name", val);
				break;
			}
		}
	}

	/**
	 * Built in rules to run after converting the element.
	 */
	private void postConvertRules(Element el, GType type) {
		// Set the name from the 'name' tag or failing that from
		// the default_name.
		el.setName(el.getTag("name"));
		if (el.getName() == null)
			el.setName(type.getDefaultName());
	}

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it. All elements that are added
	 * are clipped to this box, new points are added as needed at the boundry.
	 *
	 * If a node or a way falls completely outside the boundry then it would be
	 * ommited.  This would not normally happen in the way this option is typically
	 * used however.
	 *
	 * @param bbox The bounding area.
	 */
	public void setBoundingBox(Area bbox) {
		this.clipper = new AreaClipper(bbox);
	}

	/**
	 * Run the rules for this relation.  As this is not an end object, then
	 * the only useful rules are action rules that set tags on the contained
	 * ways or nodes.  Every rule should probably start with 'type=".."'.
	 *
	 * @param relation The relation to convert.
	 */
	public void convertRelation(Relation relation) {
		// Relations never resolve to a GType and so we ignore the return
		// value.
		relationRules.resolveType(relation);
	}

	private void addLine(Way way, GType gt) {
		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(way.getPoints());

		if (way.isBoolTag("oneway"))
			line.setDirection(true);

		clipper.clipLine(line, lineAdder);
	}

	private void addShape(Way way, GType gt) {
		MapShape shape = new MapShape();
		elementSetup(shape, gt, way);
		shape.setPoints(way.getPoints());

		clipper.clipShape(shape, collector);
	}

	private void addPoint(Node node, GType gt) {
		if (!clipper.contains(node.getLocation()))
			return;

		MapPoint mp = new MapPoint();
		elementSetup(mp, gt, node);
		mp.setLocation(node.getLocation());

		collector.addPoint(mp);
	}

	private void elementSetup(MapElement ms, GType gt, Element element) {
		ms.setName(element.getName());
		ms.setType(gt.getType());
		ms.setMinResolution(gt.getMinResolution());
		ms.setMaxResolution(gt.getMaxResolution());
	}

	void addRoad(Way way, GType gt) {

		if("roundabout".equals(way.getTag("junction"))) {
			String frigFactorTag = way.getTag("mkgmap:frig_roundabout");
			if(frigFactorTag != null) {
				// do special roundabout frigging to make gps
				// routing prompt use the correct exit number
				double frigFactor = 0.25; // default
				try {
					frigFactor = Double.parseDouble(frigFactorTag);
				}
				catch (NumberFormatException nfe) {
					// relax, tag was probably not a number anyway
				}
				frigRoundabout(way, frigFactor);
			}
		}

		// check if the way is a loop or intersects with itself

		boolean wayWasSplit = true; // aka rescan required

		while(wayWasSplit) {
			List<Coord> wayPoints = way.getPoints();
			int numPointsInWay = wayPoints.size();

			wayWasSplit = false; // assume way won't be split

			// check each point in the way to see if it is
			// the same point as a following point in the way
			for(int p1I = 0; !wayWasSplit && p1I < (numPointsInWay - 1); p1I++) {
				Coord p1 = wayPoints.get(p1I);
				for(int p2I = p1I + 1; !wayWasSplit && p2I < numPointsInWay; p2I++) {
					if(p1 == wayPoints.get(p2I)) {
						// way is a loop or intersects itself
						int splitI = p2I - 1; // split before second point
						if(splitI == p1I) {
							System.err.println("Way has zero length segment at " + wayPoints.get(splitI).toDegreeString());
							wayPoints.remove(p2I);
							// next point to inspect has same index
							--p2I;
							// but number of points has reduced
							--numPointsInWay;
						}
						else {
							// split the way before the second point
							//System.err.println("Split way at " + wayPoints.get(splitI).toDegreeString() + " - it has " + (numPointsInWay - splitI - 1 ) + " following segments.");
							Way loopTail = splitWayAt(way, splitI);
							// way before split has now been verified
							addRoadWithoutLoops(way, gt);
							// now repeat for the tail of the way
							way = loopTail;
							wayWasSplit = true;
						}
					}
				}
			}

			if(!wayWasSplit) {
				// no split required so make road from way
				addRoadWithoutLoops(way, gt);
			}
		}
	}

	void addRoadWithoutLoops(Way way, GType gt) {
		List<Integer> nodeIndices = new ArrayList<Integer>();
		List<Coord> points = way.getPoints();
		Way trailingWay = null;

		// make sure the way has nodes at each end
		points.get(0).incHighwayCount();
		points.get(points.size() - 1).incHighwayCount();

		// collect the Way's nodes
		for(int i = 0; i < points.size(); ++i) {
			Coord p = points.get(i);
			int highwayCount = p.getHighwayCount();
			if(highwayCount > 1) {
				// this point is a node connecting highways
				Integer nodeId = nodeIdMap.get(p);
				if(nodeId == null) {
					// assign a node id
					nodeId = nextNodeId++;
					nodeIdMap.put(p, nodeId);
				}
				nodeIndices.add(i);
		//		System.err.println("Found node " + nodeId + " at " + p.toDegreeString());

				if((i + 1) < points.size() &&
				   nodeIndices.size() == MAX_NODES_IN_WAY) {
					// this isn't the last point in the way
					// so split it here to avoid exceeding
					// the max nodes in way limit
					trailingWay = splitWayAt(way, i);
					// this will have truncated
					// the current Way's points so
					// the loop will now terminate
					//					System.err.println("Splitting way " + way.getName() + " at " + points.get(i).toDegreeString() + " as it has at least " + MAX_NODES_IN_WAY + " nodes");
				}
			}
		}

		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(points);

		if(way.isBoolTag("oneway"))
			line.setDirection(true);

		MapRoad road = new MapRoad(roadId++, line);

		// set road parameters.
		road.setRoadClass(gt.getRoadClass());
		road.setOneway(line.isDirection());
		
		// maxspeed attribute overrides default for road type
		
		String maxSpeed = way.getTag("maxspeed");
		int speedIdx = -1;
		
		if(maxSpeed != null)
			speedIdx = getSpeedIdx(maxSpeed);

		road.setSpeed(speedIdx >= 0? speedIdx : gt.getRoadSpeed());

		boolean[] noAccess = new boolean[RoadNetwork.NO_MAX];
		String highwayType = way.getTag("highway");
		if(highwayType == null) {
			// it's a routable way but not a highway (e.g. a ferry)
			// use the value of the route tag as the highwayType
			// for the purpose of testing for access restrictions
			highwayType = way.getTag("route");
		}

		String[] vehicleClass = { "access",
					  "bicycle",
					  "foot",
					  "hgv",
					  "motorcar",
					  "motorcycle",
					  "psv",
		};
		int[] accessSelector = { RoadNetwork.NO_MAX,
					 RoadNetwork.NO_BIKE,
					 RoadNetwork.NO_FOOT,
					 RoadNetwork.NO_TRUCK,
					 RoadNetwork.NO_CAR,
					 RoadNetwork.NO_CAR, // motorcycle
					 RoadNetwork.NO_BUS };

		for(int i = 0; i < vehicleClass.length; ++i) {
			String access = way.getTag(vehicleClass[i]);
			if(access != null)
				access = access.toUpperCase();
			if(access != null &&
			   (access.equals("PRIVATE") ||
			    access.equals("DESTINATION") || // FIXME - too strict
			    access.equals("UNKNOWN") ||
			    access.equals("NO"))) {
				if(accessSelector[i] == RoadNetwork.NO_MAX) {
					// everything is denied access
					for(int j = 0; j < noAccess.length; ++j)
						noAccess[j] = true;
				}
				else {
					// just the specific vehicle
					// class is denied access
					noAccess[accessSelector[i]] = true;
				}
				//System.err.println("No " + vehicleClass[i] + " allowed in " + highwayType + " " + way.getName());
			}
		}

		if("footway".equals(highwayType)) {
			noAccess[RoadNetwork.EMERGENCY] = true;
			noAccess[RoadNetwork.DELIVERY] = true;
			noAccess[RoadNetwork.NO_CAR] = true;
			noAccess[RoadNetwork.NO_BUS] = true;
			noAccess[RoadNetwork.NO_TAXI] = true;
			if(!way.isBoolTag("bicycle"))
				noAccess[RoadNetwork.NO_BIKE] = true;
			noAccess[RoadNetwork.NO_TRUCK] = true;
		}
		else if("cycleway".equals(highwayType) || "bridleway".equals(highwayType)) {
			noAccess[RoadNetwork.EMERGENCY] = true;
			noAccess[RoadNetwork.DELIVERY] = true;
			noAccess[RoadNetwork.NO_CAR] = true;
			noAccess[RoadNetwork.NO_BUS] = true;
			noAccess[RoadNetwork.NO_TAXI] = true;
			noAccess[RoadNetwork.NO_TRUCK] = true;
		}

		road.setAccess(noAccess);

		if(way.isBoolTag("toll"))
			road.setToll(true);

		//road.setDirIndicator(dirIndicator); // FIXME

		int numNodes = nodeIndices.size();
		road.setNumNodes(numNodes);

		if(numNodes > 0) {
			// replace Coords that are nodes with CoordNodes
			boolean hasInternalNodes = false;
			for(int i = 0; i < numNodes; ++i) {
				int n = nodeIndices.get(i);
				if(n > 0 && n < points.size() - 1)
					hasInternalNodes = true;
				Coord coord = points.get(n);
				Integer nodeId = nodeIdMap.get(coord);
				boolean boundary = way.isBoolTag("mkgmap:boundary_node");
				points.set(n, new CoordNode(coord.getLatitude(), coord.getLongitude(), nodeId, boundary));
				//		System.err.println("Road " + road.getRoadId() + " node[" + i + "] " + nodeId + " at " + coord.toDegreeString());
			}

			road.setStartsWithNode(nodeIndices.get(0) == 0);
            road.setEndsWithNode(nodeIndices.get(numNodes-1) == points.size() - 1);
			road.setInternalNodes(hasInternalNodes);
		}

		clipper.clipLine(road, lineAdder);

		if(trailingWay != null)
		    addRoadWithoutLoops(trailingWay, gt);
	}

	// split a Way at the specified point and return the new Way
        // (the original Way is truncated)

	Way splitWayAt(Way way, int index) {
		Way trailingWay = new Way();
		List<Coord> wayPoints = way.getPoints();
		int numPointsInWay = wayPoints.size();

		for(int i = index; i < numPointsInWay; ++i)
			trailingWay.addPoint(wayPoints.get(i));

		// ensure split point becomes a node
		wayPoints.get(index).incHighwayCount();

		// copy the way's name and tags to the new way
		trailingWay.setName(way.getName());
		trailingWay.copyTags(way);

		// remove the points after the split from the original way
		// it's probably more efficient to remove from the end first
		for(int i = numPointsInWay - 1; i > index; --i)
			wayPoints.remove(i);

		return trailingWay;
	}

	// function to add points between adjacent nodes in a roundabout
	// to make gps use correct exit number in routing instructions
	void frigRoundabout(Way way, double frigFactor) {
		List<Coord> wayPoints = way.getPoints();
		int origNumPoints = wayPoints.size();

		if(origNumPoints < 3) {
			// forget it!
			return;
		}

		int[] highWayCounts = new int[origNumPoints];
		int middleLat = 0;
		int middleLon = 0;
		highWayCounts[0] = wayPoints.get(0).getHighwayCount();
		for(int i = 1; i < origNumPoints; ++i) {
			Coord p = wayPoints.get(i);
			middleLat += p.getLatitude();
			middleLon += p.getLongitude();
			highWayCounts[i] = p.getHighwayCount();
		}
		middleLat /= origNumPoints - 1;
		middleLon /= origNumPoints - 1;
		Coord middleCoord = new Coord(middleLat, middleLon);

		// account for fact that roundabout joins itself
		--highWayCounts[0];
		--highWayCounts[origNumPoints - 1];

		for(int i = origNumPoints - 2; i >= 0; --i) {
			Coord p1 = wayPoints.get(i);
			Coord p2 = wayPoints.get(i + 1);
			if(highWayCounts[i] > 1 && highWayCounts[i + 1] > 1) {
				// both points will be nodes so insert
				// a new point between them that
				// (approximately) falls on the
				// roundabout's perimeter
				int newLat = (p1.getLatitude() + p2.getLatitude()) / 2;
				int newLon = (p1.getLongitude() + p2.getLongitude()) / 2;
				// new point has to be "outside" of
				// existing line joining p1 and p2 -
				// how far outside is determined by
				// the ratio of the distance between
				// p1 and p2 compared to the distance
				// of p1 from the "middle" of the
				// roundabout (aka, the approx radius
				// of the roundabout) - the higher the
				// value of frigFactor, the further out
				// the point will be
				double scale = 1 + frigFactor * p1.distance(p2) / p1.distance(middleCoord);
				newLat = (int)((newLat - middleLat) * scale) + middleLat;
				newLon = (int)((newLon - middleLon) * scale) + middleLon;
				Coord newPoint = new Coord(newLat, newLon);
				double d1 = p1.distance(newPoint);
				double d2 = p2.distance(newPoint);
				double minDistance = 5.5;
				double maxDistance = 100;
				if(d1 >= minDistance && d1 <= maxDistance &&
				   d2 >= minDistance && d2 <= maxDistance) {
				    newPoint.incHighwayCount();
				    wayPoints.add(i + 1, newPoint);
				}
				else if(false) {
				    System.err.println("Not inserting point in roundabout after node " +
						       i + " " +
						       way.getName() +
						       " @ " + 
						       middleCoord.toDegreeString() +
						       " (d1 = " +
						       p1.distance(newPoint) +
						       " d2 = " +
						       p2.distance(newPoint) +
						       ")");
				}
			}
		}
	}

	int getSpeedIdx(String tag)
	{
		double kmh = 0.0;
		double factor = 1.0;
		
		String speedTag = tag.toLowerCase().trim();
		
		if(speedTag.matches(".*mph")) // Check if it is a limit in mph
		{
			speedTag = speedTag.replaceFirst("mph", "");
			factor = 1.61;
		}
		else
			speedTag = speedTag.replaceFirst("kmh", "");  // get rid of kmh just in case
		
		try {
			kmh = Integer.parseInt(speedTag) * factor;
		}
		catch (Exception e)
		{
			return -1;
		}
		
		if(kmh > 110)
			return 7;
		if(kmh > 90)
			return 6;
		if(kmh > 80)
			return 5;
		if(kmh > 60)
			return 4;
		if(kmh > 40)
			return 3;
		if(kmh > 20)
			return 2;
		if(kmh > 10)
			return 1;
		else
			return 0;	    

	}
}
