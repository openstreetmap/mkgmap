/*
 * Copyright (C) 2013.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.MultiIdentityHashMap;

public class RoadMerger {
	private static final Logger log = Logger.getLogger(RoadMerger.class);

	// maps which coord of a way(id) are restricted - they should not be merged
	private final MultiIdentityHashMap<Coord, Long> restrictions;
	private final List<Road> roads;

	private final MultiIdentityHashMap<Coord, Road> startPoints = new MultiIdentityHashMap<Coord, Road>();
	private final MultiIdentityHashMap<Coord, Road> endPoints = new MultiIdentityHashMap<Coord, Road>();

	private static class Road {
		private final Way way;
		private final GType gtype;

		private final static Set<String> valueNotBoolCompareTags = new HashSet<String>() {
			{
				add("mkgmap:emergency");
				add("mkgmap:delivery");
				add("mkgmap:car");
				add("mkgmap:bus");
				add("mkgmap:taxi");
				add("mkgmap:foot");
				add("mkgmap:bicycle");
				add("mkgmap:truck");
				add("mkgmap:throughroute");
			}
		};

		private final static Set<String> valueBoolCompareTags = new HashSet<String>() {
			{
				add("mkgmap:carpool");
				add("mkgmap:toll");
				add("mkgmap:unpaved");
				add("mkgmap:ferry");
			}
		};
		
		private final static Set<String> flatCompareTags = new HashSet<String>() {
			{
				add("mkgmap:ref");
				add("mkgmap:display_name");
				add("mkgmap:postal_code");
				add("mkgmap:city");
				add("mkgmap:region");
				add("mkgmap:country");
				add("mkgmap:is_in");
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

			// check if that would create a closed way - this should no be
			// created
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
			// log.info("Gtype1",gtype);
			// log.info("Gtype2",otherGType);
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
			// log.info("Matches");
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
				log.debug("oneway does not match", way.getId(), "("
						+ thisOneway + ")", otherWay.getId(), "(" + otherOneway
						+ ")");
				// log.warn(way.getId(), way.toTagString());
				// log.warn(otherWay.getId(), otherWay.toTagString());
				return false;
			} else if ("yes".equals(thisOneway) || "-1".equals(thisOneway)) {
				boolean thisStart = getWay().getPoints().get(0)
						.equals(mergePoint);
				boolean otherStart = otherWay.getPoints().get(0)
						.equals(mergePoint);
				if (thisStart == otherStart) {
					// both ways are oneway but they have a different direction
					log.warn("oneway with different direction", way.getId(),
							otherWay.getId());
					return false;
				}
			}

			for (String tagname : flatCompareTags) {
				String thisTag = getWay().getTag(tagname);
				String otherTag = otherWay.getTag(tagname);
				if (stringEquals(thisTag, otherTag) == false) {
					log.debug(tagname, "does not match", way.getId(), "("
							+ thisTag + ")", otherWay.getId(), "(" + otherTag
							+ ")");
					// log.warn(way.getId(), way.toTagString());
					// log.warn(otherWay.getId(), otherWay.toTagString());
					return false;
				}
			}

			for (String tagname : valueNotBoolCompareTags) {
				boolean thisNo = getWay().isNotBoolTag(tagname);
				boolean otherNo = otherWay.isNotBoolTag(tagname);
				if (thisNo != otherNo) {
					log.debug(tagname, "does not match", way.getId(), "("
							+ getWay().getTag(tagname) + ")", otherWay.getId(),
							"(" + otherWay.getTag(tagname) + ")");
					return false;
				}
			}

			for (String tagname : valueBoolCompareTags) {
				boolean thisYes = getWay().isBoolTag(tagname);
				boolean otherYes = otherWay.isBoolTag(tagname);
				if (thisYes != otherYes) {
					log.debug(tagname, "does not match", way.getId(), "("
							+ getWay().getTag(tagname) + ")", otherWay.getId(),
							"(" + otherWay.getTag(tagname) + ")");
					return false;
				}
			}			
			
			Coord c1;
			if (getWay().getPoints().get(0).equals(mergePoint)) {
				c1 = getWay().getPoints().get(1);
			} else {
				c1 = getWay().getPoints().get(getWay().getPoints().size() - 2);
			}
			Coord cOther;
			if (otherWay.getPoints().get(0).equals(mergePoint)) {
				cOther = otherWay.getPoints().get(1);
			} else {
				cOther = otherWay.getPoints().get(
						otherWay.getPoints().size() - 2);
			}

			double angle = Math.abs(Utils.getAngle(c1, mergePoint, cOther));
			if (angle > 130) {
				// log.error("Bearing "+b+" "+getWay().getId()+" "+otherWay.getId());
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
			Map<Coord, List<RestrictionRelation>> restrictions,
			List<Relation> throughRouteRelations) {
		assert ways.size() == gtypes.size();

		this.roads = new ArrayList<Road>(ways.size());

		for (int i = 0; i < ways.size(); i++) {
			if (ways.get(i) != null)
				roads.add(new Road(ways.get(i), gtypes.get(i)));
		}

		this.restrictions = new MultiIdentityHashMap<Coord, Long>();
		workoutRestrictionRelations(restrictions);
		workoutThroughRoutes(throughRouteRelations);
	}

	private void workoutRestrictionRelations(Map<Coord, List<RestrictionRelation>> restrictionRels) {
		for (List<RestrictionRelation> rels : restrictionRels.values()) {
			for (RestrictionRelation rel : rels) {
				if (rel.getViaCoord() == null) {
					continue;
				}
				if (rel.getFromWay() != null) {
					restrictions.add(rel.getViaCoord(), rel.getFromWay().getId());
				}
				if (rel.getToWay() != null) {
					restrictions.add(rel.getViaCoord(), rel.getToWay().getId());
				}
			}
		}
	}
	
	private void workoutThroughRoutes(List<Relation> throughRouteRelations) {
		for (Relation relation : throughRouteRelations) {
			Node node = null;
			Way w1 = null;
			Way w2 = null;
			for (Map.Entry<String, Element> member : relation.getElements()) {
				if (member.getValue() instanceof Node) {
					if (node == null)
						node = (Node) member.getValue();
					else
						log.warn("Through route relation "
								+ relation.toBrowseURL()
								+ " has more than 1 node");
				} else if (member.getValue() instanceof Way) {
					Way w = (Way) member.getValue();
					if (w1 == null)
						w1 = w;
					else if (w2 == null)
						w2 = w;
					else
						log.warn("Through route relation "
								+ relation.toBrowseURL()
								+ " has more than 2 ways");
				}
			}

			if (node == null)
				log.warn("Through route relation " + relation.toBrowseURL()
						+ " is missing the junction node");

			if (w1 == null || w2 == null)
				log.warn("Through route relation "
						+ relation.toBrowseURL()
						+ " should reference 2 ways that meet at the junction node");

			if (node != null && w1 != null && w2 != null) {
				restrictions.add(node.getLocation(), w1.getId());
				restrictions.add(node.getLocation(), w2.getId());
			}
		}
	}

	private boolean hasRestriction(Coord c, Way w) {
		List<Long> wayRestrictions = restrictions.get(c);
		return wayRestrictions.contains(w.getId());
	}

	private void mergeRoads(Road road1, Road road2) {
		// Removes the second line,
		// Merges the points in the first one
		List<Coord> points1 = road1.getWay().getPoints();
		List<Coord> points2 = road2.getWay().getPoints();

		Coord mergePoint = points2.get(0);
		Coord endPoint= points2.get(points2.size()-1);
		
		startPoints.remove(mergePoint, road2);
		endPoints.remove(endPoint, road2);
		endPoints.remove(mergePoint, road1);

		points1.addAll(points2.subList(1, points2.size()));
		endPoints.add(endPoint, road1);
		
//		// the mergePoint is now used by one highway less
		mergePoint.decHighwayCount();
		
		// road2 is removed - it must not be part of a restriction
		assert (restrictions.get(endPoint).contains(road2.getWay().getId()) == false);
		
	}

	public void merge(List<Way> resultingWays, List<GType> resultingGTypes) {

		int noRoadsBeforeMerge = this.roads.size();
		int noMerges = 0;
		List<Road> roadsToMerge = new ArrayList<Road>(this.roads);
		this.roads.clear();

		// first add all roads with their start and end points to the
		// start/endpoint lists
		for (Road road : roadsToMerge) {
			List<Coord> points = road.getWay().getPoints();
			Coord start = points.get(0);
			Coord end = points.get(points.size() - 1);

			if (start.equals(end)) {
				// do not merge closed roads
				roads.add(road);
				continue;
			}

			startPoints.add(start, road);
			endPoints.add(end, road);
		}

		HashSet<Coord> fullMergedPoints = new HashSet<Coord>();
		boolean oneRoadMerged = true;
		
		while (oneRoadMerged) {
			oneRoadMerged = false;
			HashSet<Coord> mergePoints = new HashSet<Coord>(endPoints.keySet());
			mergePoints.retainAll(startPoints.keySet());
			// remove all coords that have been completely processed and that have no more merge candidates
			mergePoints.removeAll(fullMergedPoints);
			
			for (Coord mergePoint : mergePoints) {
				List<Road> endRoads = endPoints.get(mergePoint);
				List<Road> startRoads = startPoints.get(mergePoint);
				
				if (endRoads.isEmpty() || startRoads.isEmpty()) {
					// this might happen if another merge operation changed endPoints and/or startPoints
					continue;
				}
				
				
				// go through all combinations and test which combination is the best
				double bestAngle = Double.MAX_VALUE;
				Road mergeRoad1 = null;
				Road mergeRoad2 = null;
				
				for (Road road1 : endRoads) {
					List<Coord> points1 = road1.getWay().getPoints();
					for (Road road2 : startRoads) {
						if (hasRestriction(mergePoint, road1.getWay())) {
							continue;
						}
						if (hasRestriction(mergePoint, road2.getWay())) {
							continue;
						}
						List<Coord> points2 = road2.getWay().getPoints();
						if (hasRestriction(points2.get(points2.size()-1), road2.getWay())) {
							continue;
						}
						if (road1.isMergable(mergePoint, road2)) {
							double angle = Math.abs(Utils.getAngle(points1.get(points1.size()-2), mergePoint, points2.get(1)));
							log.debug("Road",road1.getWay().getId(),"and road",road2.getWay().getId(),"are mergeable with angle",angle);
							if (angle < bestAngle) {
								mergeRoad1 = road1;
								mergeRoad2 = road2;
								bestAngle = angle;
							}
						}
					}
				}
				
				if (mergeRoad1 != null && mergeRoad2 != null) {
					log.debug("Merge ",mergeRoad1.getWay().getId(),"and",mergeRoad2.getWay().getId(),"with angle",bestAngle);
					mergeRoads(mergeRoad1, mergeRoad2);
					oneRoadMerged = true;
					noMerges++;
				} else {
					fullMergedPoints.add(mergePoint);
				}
			}
		}

		// copy all merged roads to the roads list
		for (List<Road> mergedRoads : endPoints.values()) {
			this.roads.addAll(mergedRoads);
		}

		// copy the roads to the resulting lists
		for (Road r : roads) {
			resultingWays.add(r.getWay());
			resultingGTypes.add(r.getGtype());
		}

		int noRoadsAfterMerge = this.roads.size();
		log.info("Roads before/after merge:", noRoadsBeforeMerge, "/",
				noRoadsAfterMerge);
		int percentage = (int) Math
				.round((noRoadsBeforeMerge - noRoadsAfterMerge) * 100.0d
						/ noRoadsBeforeMerge);
		log.info("Road network reduced by", percentage, "%",noMerges,"merges");
	}
}
