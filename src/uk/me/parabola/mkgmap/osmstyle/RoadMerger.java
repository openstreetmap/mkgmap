package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.MultiHashMap;

public class RoadMerger {
	private static final Logger log = Logger.getLogger(RoadMerger.class);

	private final Map<Coord, List<RestrictionRelation>> restrictions;
	private final MultiHashMap<Coord, Way> throughRoutes;
	private final List<Road> roads;

	private final MultiHashMap<Coord, Road> startPoints = new MultiHashMap<Coord, Road>();
	private final MultiHashMap<Coord, Road> endPoints = new MultiHashMap<Coord, Road>();

	private static class Road {
		private final Way way;
		private final GType gtype;
	
		private final static Set<String> flatCompareTags = new HashSet<String>() {
			{
				add("mkgmap:ref");
				add("mkgmap:display_name");
				add("mkgmap:postal_code");
				add("mkgmap:city");
				add("mkgmap:region");
				add("mkgmap:country");
				add("mkgmap:is_in");
				add("access");
				add("bicycle");
				add("carpool");
				add("foot");
				add("hgv");
				add("motorcar");
				add("motorcycle");
				add("psv");
				add("taxi");
				add("emergency");
				add("delivery");
				add("goods");
				add("mkgmap:skipSizeFilter");
				add("junction");
				add("mkgmap:synthesised");
				add("mkgmap:flare-check");
				add("mkgmap:road-class");
				add("mkgmap:road-class-max");
				add("mkgmap:road-class-min");
				add("mkgmap:road-speed-class");
				add("mkgmap:road-speed");
				add("mkgmap:road-speed-max");
				add("mkgmap:road-speed-min");
				add("mkgmap:carpool");
				add("mkgmap:toll");
				add("mkgmap:unpaved");
				add("mkgmap:ferry");
			}
		};
		

		public Road(Way way, GType gtype) {
			this.way = way;
			this.gtype = gtype;
		}

		public boolean isMergable(Coord mergePoint, Road otherRoad) {
			Coord cStart = way.getPoints().get(0);
			Coord cEnd = way.getPoints().get(way.getPoints().size() - 1);
			if (cStart.equals(mergePoint) == false
					&& cEnd.equals(mergePoint) == false) {
				return false;
			}

			Coord cOtherStart = otherRoad.getWay().getPoints().get(0);
			Coord cOtherEnd = otherRoad.getWay().getPoints()
					.get(otherRoad.getWay().getPoints().size() - 1);
			if (cOtherStart.equals(mergePoint) == false
					&& cOtherEnd.equals(mergePoint) == false) {
				return false;
			}

			// check if that would create a closed way - this should no be created
			if (cStart.equals(cOtherEnd)) {
				return false;
			}
			
			if (isGTypeMergable(otherRoad.getGtype()) == false) {
				return false;
			}

			if (isWayMergable(mergePoint, otherRoad.getWay()) == false) {
				return false;
			}

			return true;
		}

		private boolean isGTypeMergable(GType otherGType) {
//			log.info("Gtype1",gtype);
//			log.info("Gtype2",otherGType);
			if (gtype.getType() != otherGType.getType()) {
				return false;
			}
			if (gtype.getRoadClass() != otherGType.getRoadClass()) {
				return false;
			}
			if (gtype.getRoadSpeed() != otherGType.getRoadSpeed()) {
				return false;
			}
			if (gtype.getMinResolution() != otherGType.getMinResolution()) {
				return false;
			}
			if (gtype.getMaxResolution() != otherGType.getMaxResolution()) {
				return false;
			}
			if (gtype.getMinLevel() != otherGType.getMinLevel()) {
				return false;
			}
			if (gtype.getMaxLevel() != otherGType.getMaxLevel()) {
				return false;
			}
			if (stringEquals(gtype.getDefaultName(),
					otherGType.getDefaultName()) == false) {
				return false;
			}
//			log.info("Matches");
			return true;
		}

		private boolean isWayMergable(Coord mergePoint, Way otherWay) {
			
			if (stringEquals(getWay().getName(), otherWay.getName()) == false) {
				return false;
			}
			
			// special oneway handling
			String thisOneway = getWay().getTag("oneway");
			if (thisOneway == null) {
				thisOneway = "no";
			} else {
				if (thisOneway.equals("true") || thisOneway.equals("1")) {
					thisOneway = "yes";
				} else if (thisOneway.equals("false")) {
					thisOneway = "no";
				} else if (thisOneway.equals("reverse")) {
					thisOneway = "-1";
				}
			}
			String otherOneway = otherWay.getTag("oneway");
			if (otherOneway == null) {
				otherOneway = "no";
			} else {
				if (otherOneway.equals("true") || otherOneway.equals("1")) {
					otherOneway = "yes";
				} else if (otherOneway.equals("false")) {
					otherOneway = "no";
				} else if (otherOneway.equals("reverse")) {
					otherOneway = "-1";
				}
			}			
			
			if (stringEquals(thisOneway, otherOneway) == false) {
				log.debug("oneway does not match", way.getId(),"("+thisOneway+")", otherWay.getId(),"("+otherOneway+")");
//				log.warn(way.getId(), way.toTagString());
//				log.warn(otherWay.getId(), otherWay.toTagString());
				return false;
			} else if ("yes".equals(thisOneway) || "-1".equals(thisOneway)) {
				boolean thisStart = getWay().getPoints().get(0).equals(mergePoint);
				boolean otherStart = otherWay.getPoints().get(0).equals(mergePoint);
				if (thisStart == otherStart) {
					// both ways are oneway but they have a different direction
					log.warn("oneway with different direction", way.getId(), otherWay.getId());
					return false;
				}
			}
			
			for (String tagname : flatCompareTags) {
				String thisTag = getWay().getTag(tagname);
				String otherTag = otherWay.getTag(tagname);
				if (stringEquals(thisTag, otherTag) == false) {
					log.debug(tagname,"does not match",way.getId(),"("+thisTag+")",otherWay.getId(),"("+otherTag+")");
//					log.warn(way.getId(), way.toTagString());
//					log.warn(otherWay.getId(), otherWay.toTagString());
					return false;
				}
			}
	
			Coord c1;
			if (getWay().getPoints().get(0).equals(mergePoint)) {
				c1 = getWay().getPoints().get(1);
			} else {
				c1 = getWay().getPoints().get(getWay().getPoints().size()-2);
			}
			Coord cOther;
			if (otherWay.getPoints().get(0).equals(mergePoint)) {
				cOther = otherWay.getPoints().get(1);
			} else {
				cOther = otherWay.getPoints().get(otherWay.getPoints().size()-2);
			}

			double a = c1.bearingTo(mergePoint);
			double b = mergePoint.bearingTo(cOther) - a;
			while(b > 180)
				b -= 360;
			while(b < -180)
				b += 360;
			
			b = Math.abs(b);
			if (b > 130) {
//				log.error("Bearing "+b+" "+getWay().getId()+" "+otherWay.getId());
				return false;
			}
			
			return true;
		}

		public Way getWay() {
			return way;
		}

		public GType getGtype() {
			return gtype;
		}

		private boolean stringEquals(String s1, String s2) {
			if (s1 == null) {
				return s2 == null;
			} else {
				return s1.equals(s2);
			}
		}

		public String toString() {
			return gtype + " " + way.getId() + " " + way.toTagString();
		}
	}

	public RoadMerger(List<Way> ways, List<GType> gtypes,
			Map<Coord, List<RestrictionRelation>> restrictions, List<Relation> throughRouteRelations) {
		assert ways.size() == gtypes.size();
		
		this.roads = new ArrayList<Road>(ways.size());

		for (int i = 0; i < ways.size(); i++) {
			roads.add(new Road(ways.get(i), gtypes.get(i)));
		}

		this.restrictions = restrictions;
		this.throughRoutes = new MultiHashMap<Coord, Way>();
		workoutThroughRoutes(throughRouteRelations);
	}

	private void workoutThroughRoutes(List<Relation> throughRouteRelations) {
		for(Relation relation : throughRouteRelations) {
			Node node = null;
			Way w1 = null;
			Way w2 = null;
			for(Map.Entry<String,Element> member : relation.getElements()) {
				if(member.getValue() instanceof Node) {
					if(node == null)
						node = (Node)member.getValue();
					else
						log.warn("Through route relation " + relation.toBrowseURL() + " has more than 1 node");
				}
				else if(member.getValue() instanceof Way) {
					Way w = (Way)member.getValue();
					if(w1 == null)
						w1 = w;
					else if(w2 == null)
						w2 = w;
					else
						log.warn("Through route relation " + relation.toBrowseURL() + " has more than 2 ways");
				}
			}

			if(node == null)
				log.warn("Through route relation " + relation.toBrowseURL() + " is missing the junction node");

			if(w1 == null || w2 == null)
				log.warn("Through route relation " + relation.toBrowseURL() + " should reference 2 ways that meet at the junction node");

			if(node != null && w1 != null && w2 != null) {
				throughRoutes.add(node.getLocation(), w1);
				throughRoutes.add(node.getLocation(), w2);
			}
		}
	}
	
	private boolean hasRestriction(Coord c, Way w) {
		List<RestrictionRelation> wayRestrictions = restrictions.get(c);
		if (wayRestrictions != null) {
			for (RestrictionRelation r : wayRestrictions) {
				if (w.equals(r.getFromWay()) || w.equals(r.getToWay())) {
					return true;
				}
			}
		}
		
		List<Way> throughRouteWays = throughRoutes.get(c);
		if (throughRouteWays.contains(w)) {
			log.error("Through route "+c+" "+throughRouteWays);
			return true;
		}
		return false;
	}

	private void addPointsAtStart(Road road, List<Coord> additionalPoints) {
		List<Coord> points = road.getWay().getPoints();
		startPoints.remove(points.get(0), road);
		points.addAll(0, additionalPoints.subList(0, additionalPoints.size() - 1));
		startPoints.add(points.get(0), road);
	}
	
	private void addPointsAtEnd(Road road, List<Coord> additionalPoints) {
		List<Coord> points = road.getWay().getPoints();
		endPoints.remove(points.get(points.size()-1), road);
		points.addAll(points.subList(1, points.size()));
		endPoints.add(points.get(points.size()-1), road);
	}
	
	private void mergeLines(Road road1, Road road2) {
		// Removes the first line,
		// Merges the points in the second one
		List<Coord> points1 = road1.getWay().getPoints();
		List<Coord> points2 = road2.getWay().getPoints();
		startPoints.remove(points1.get(0), road1);
		endPoints.remove(points1.get(points1.size()-1), road1);
		startPoints.remove(points2.get(0), road2);
		startPoints.add(points1.get(0), road2);
		points2.addAll(0, points1.subList(0, points1.size()-1));
		roads.remove(road1);
	}
	
	public void merge(List<Way> resultingWays, List<GType> resultingGTypes) {
		int noRoadsBeforeMerge = this.roads.size();
		List<Road> roadsToMerge = new ArrayList<Road>(this.roads);
		this.roads.clear();
		
		for (Road road : roadsToMerge) {
			boolean isMerged = false;

			List<Coord> points = road.getWay().getPoints();
			Coord start = points.get(0);
			Coord end = points.get(points.size() - 1);

			if (start.equals(end)) {
				// do not merge closed roads
				roads.add(road);
				continue;
			}
			
			boolean endRestriction = hasRestriction(end, road.getWay()); 
			if (endRestriction == false) {
				// Search for start point in hashlist
				// (can the end of current line connected to an existing line?)
				for (Road road2 : startPoints.get(end)) {
					if (road.isMergable(end, road2)) {
//						log.error("=== Merge " + end + " ===");
//						log.error(road);
//						log.error(road2);
						
						addPointsAtStart(road2, points);
						isMerged = true;
						
						// The start point of the road is the new start point
						// of road2. Try to merge this start point of
						// road2 also
						for (Road road1 : endPoints.get(start)) {
							if (road2.isMergable(start, road1)
									&& !road2.equals(road1)) // don't make a
																// closed
																// loop a double
																// loop
							{
//								log.error("=== Merge " + start + " ===");
//								log.error(road1);
//								log.error(road2);
								mergeLines(road1, road2);
								break;
							}
						}
						break;
					}
				}
			}
			if (isMerged)
				continue;

			boolean startRestriction = hasRestriction(start, road.getWay());
			if (startRestriction == false) {
				// Search for endpoint in hashlist
				// (can the start of current line connected to an existing
				// line?)
				for (Road road2 : endPoints.get(start)) {
					if (road.isMergable(start, road2)) {
//						log.error("=== Merge " + start + " ===");
//						log.error(road);
//						log.error(road2);
						addPointsAtEnd(road2, points);
						isMerged = true;
						break;
					}
				}
			}
			if (isMerged)
				continue;

			points = road.getWay().getPoints();
			startPoints.add(points.get(0), road);
			endPoints.add(points.get(points.size() - 1), road);

			roads.add(road);
		}

		// copy the roads to the resulting lists
		for (Road r : roads) {
			resultingWays.add(r.getWay());
			resultingGTypes.add(r.getGtype());
		}
		
		int noRoadsAfterMerge = this.roads.size();
		log.info("Roads before/after merge:", noRoadsBeforeMerge, "/", noRoadsAfterMerge);
		int percentage = (int) Math.round((noRoadsBeforeMerge-noRoadsAfterMerge)*100.0d/noRoadsBeforeMerge);
		log.info("Road network reduced by",percentage,"%");
	}
}
