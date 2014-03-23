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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
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

/**
 * Merges connected roads with identical road relevant tags based on the OSM elements 
 * and the GType class.
 * 
 * @author WanMil
 */
public class RoadMerger {
	private static final Logger log = Logger.getLogger(RoadMerger.class);

	private static final double MAX_MERGE_ANGLE = 130d;
	
	private final MultiIdentityHashMap<Coord, Long> restrictions;
	/** Contains a list of all roads (GType + Way) */
	private final List<Road> roads;

	/** maps the start point of a road to its road definition */
	private final MultiIdentityHashMap<Coord, Road> startPoints = new MultiIdentityHashMap<Coord, Road>();
	/** maps the end point of a road to its road definition */
	private final MultiIdentityHashMap<Coord, Road> endPoints = new MultiIdentityHashMap<Coord, Road>();

	/**
	 * Helper class to keep the Way and the GType object of a road. 
	 * Also provides methods that are able to decide if two roads can 
	 * be merged.
	 * 
	 * @author WanMil
	 */
	private static class Road {
		/** gives the index of the original position in the way/road list */
		private final int index;
		private final Way way;
		private final GType gtype;

		/** 
		 * For these tags two ways need to return the same value for {@link Way#isNotBoolTag(String)} 
		 * so that their roads can be merged.
		 */
		private final static Set<String> mergeTagsNotBool = new HashSet<String>() {
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

		/** 
		 * For these tags two ways need to return the same value for {@link Way#isBoolTag(String)} 
		 * so that their roads can be merged.
		 */
		private final static Set<String> mergeTagsBool = new HashSet<String>() {
			{
				add("mkgmap:carpool");
				add("mkgmap:toll");
				add("mkgmap:unpaved");
				add("mkgmap:ferry");
			}
		};
		
		/** 
		 * For these tags two ways need to have an equal value so that their roads can be merged.
		 */
		private final static Set<String> mergeTagsEqualValue = new HashSet<String>() {
			{
				add("mkgmap:label:1");
				add("mkgmap:label:2");
				add("mkgmap:label:3");
				add("mkgmap:label:4");
				add("mkgmap:postal_code");
				add("mkgmap:city");
				add("mkgmap:region");
				add("mkgmap:country");
				add("mkgmap:is_in");
				add("mkgmap:skipSizeFilter");
				add("junction");
				add("mkgmap:synthesised");
				add("mkgmap:flare-check");
			}
		};

		public Road(int index, Way way, GType gtype) {
			this.index = index;
			this.way = way;
			this.gtype = gtype;
		}

		/**
		 * Checks if the given {@code otherRoad} can be merged with this road at 
		 * the given {@code mergePoint}.
		 * @param mergePoint the coord where this road and otherRoad might be merged
		 * @param otherRoad another road instance
		 * @return {@code true} this road can be merged with {@code otherRoad};
		 * 	{@code false} the roads cannot be merged at {@code mergePoint}
		 */
		public boolean isMergable(Coord mergePoint, Road otherRoad) {
			// first check if this road starts or stops at the mergePoint
			Coord cStart = way.getPoints().get(0);
			Coord cEnd = way.getPoints().get(way.getPoints().size() - 1);
			if (cStart != mergePoint && cEnd != mergePoint) {
				// it doesn't => roads not mergeable at mergePoint
				return false;
			}

			// do the same check for the otherRoad
			Coord cOtherStart = otherRoad.getWay().getPoints().get(0);
			Coord cOtherEnd = otherRoad.getWay().getPoints()
					.get(otherRoad.getWay().getPoints().size() - 1);
			if (cOtherStart != mergePoint && cOtherEnd != mergePoint) {
				// otherRoad does not start or stop at mergePoint =>
				// roads not mergeable at mergePoint
				return false;
			}

			// check if merging would create a closed way - which should not
			// be done (why? WanMil)
			if (cStart == cOtherEnd) {
				return false;
			}
			
			// check if the GType objects are the same
			if (isGTypeMergable(otherRoad.getGtype()) == false) {
				return false;
			}
			
			// checks if the tag values of both ways match so that the ways
			// can be merged
			if (isWayMergable(mergePoint, otherRoad.getWay()) == false) {
				return false;
			}

			return true;
		}

		/**
		 * Checks if the given GType can be merged with the GType of this road.
		 * @param otherGType the GType of the other road
		 * @return {@code true} both GType objects can be merged; {@code false} GType 
		 *   objects do not match and must not be merged
		 */
		private boolean isGTypeMergable(GType otherGType) {
			// log.info("Gtype1",gtype);
			// log.info("Gtype2",otherGType);
			
			// check all fields of the GType objects for equality
			
			if (gtype.getType() != otherGType.getType()) {
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
			if (gtype.getRoadClass() != otherGType.getRoadClass()){
				return false;
			}
			if (gtype.getRoadSpeed() != otherGType.getRoadSpeed()){
				return false;
			}
// default name is applied before the RoadMerger starts
// so they needn't be equal 
//			if (stringEquals(gtype.getDefaultName(),
//					otherGType.getDefaultName()) == false) {
//				return false;
//			}
			
			// log.info("Matches");
			return true;
		}

		/**
		 * Checks if the tag values of the {@link Way} objects of both roads 
		 * match so that both roads can be merged. 
		 * @param mergePoint the coord where both roads should be merged
		 * @param otherWay the way of the road to merge
		 * @return {@code true} tag values match so that both roads might be merged;
		 *  {@code false} tag values differ so that road must not be merged
		 */
		private boolean isWayMergable(Coord mergePoint, Way otherWay) {

			// oneway must not only be checked for equal tag values
			// but also for correct direction of both ways
			
			// first map the different oneway values
			String thisOneway = getWay().getTag("oneway");
			// map oneway value for the other way
			String otherOneway = otherWay.getTag("oneway");

			if (stringEquals(thisOneway, otherOneway) == false) {
				// the oneway tags differ => cannot merge
				// (It might be possible to reverse the direction of one way
				// but this might be implemented later)
				log.debug("oneway does not match", way.getId(), "("
						+ thisOneway + ")", otherWay.getId(), "(" + otherOneway
						+ ")");
				return false;
				
			} else if ("yes".equals(thisOneway)) {
				// the oneway tags match and both ways are oneway
				// now check if both ways have the same direction
				
				boolean thisStart = (getWay().getPoints().get(0) == mergePoint);
				boolean otherStart = (otherWay.getPoints().get(0) == mergePoint);
				
				if (thisStart == otherStart) {
					// both ways are oneway but they have a different direction
					log.warn("oneway with different direction", way.getId(),
							otherWay.getId());
					return false;
				}
			}
			// oneway matches

			// now check the other tag lists
			
			// first: tags that need to have an equal value
			for (String tagname : mergeTagsEqualValue) {
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

			// second: tags for which only the NotBool value must be equal 
			for (String tagname : mergeTagsNotBool) {
				boolean thisNo = getWay().isNotBoolTag(tagname);
				boolean otherNo = otherWay.isNotBoolTag(tagname);
				if (thisNo != otherNo) {
					log.debug(tagname, "does not match", way.getId(), "("
							+ getWay().getTag(tagname) + ")", otherWay.getId(),
							"(" + otherWay.getTag(tagname) + ")");
					return false;
				}
			}

			// third: tags for which only the bool value must be equal 
			for (String tagname : mergeTagsBool) {
				boolean thisYes = getWay().isBoolTag(tagname);
				boolean otherYes = otherWay.isBoolTag(tagname);
				if (thisYes != otherYes) {
					log.debug(tagname, "does not match", way.getId(), "("
							+ getWay().getTag(tagname) + ")", otherWay.getId(),
							"(" + otherWay.getTag(tagname) + ")");
					return false;
				}
			}			
			
			// Check the angle of the two ways
			Coord c1;
			if (getWay().getPoints().get(0) == mergePoint) {
				c1 = getWay().getPoints().get(1);
			} else {
				c1 = getWay().getPoints().get(getWay().getPoints().size() - 2);
			}
			Coord cOther;
			if (otherWay.getPoints().get(0) == mergePoint) {
				cOther = otherWay.getPoints().get(1);
			} else {
				cOther = otherWay.getPoints().get(
						otherWay.getPoints().size() - 2);
			}

			double angle = Math.abs(Utils.getAngle(c1, mergePoint, cOther));
			if (angle > MAX_MERGE_ANGLE) {
				// The angle exceeds the limit => do not merge
				// Don't know if this is really required or not. 
				// But the number of merges which do not succeed due to this
				// restriction is quite low and there have been requests
				// for this: http://www.mkgmap.org.uk/pipermail/mkgmap-dev/2013q3/018649.html
				
				log.info("Do not merge ways",getWay().getId(),"and",otherWay.getId(),"because they span a too big angle",angle,"°");
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

		/**
		 * Checks if two strings are equal ({@code null} supported).
		 * @param s1 first string ({@code null} allowed)
		 * @param s2 second string ({@code null} allowed)
		 * @return {@code true} both strings are equal or both {@code null}; {@code false} both strings are not equal
		 */
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

		public final int getIndex() {
			return index;
		}
	}

	public RoadMerger(List<Way> ways, List<GType> gtypes,
			Map<Coord, List<RestrictionRelation>> restrictions,
			List<Relation> throughRouteRelations) {
		assert ways.size() == gtypes.size();

		this.roads = new ArrayList<Road>(ways.size());

		for (int i = 0; i < ways.size(); i++) {
			if (ways.get(i) != null)
				roads.add(new Road(i, ways.get(i), gtypes.get(i)));
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
		if (c.isViaNodeOfRestriction())
			return true;
		List<Long> wayRestrictions = restrictions.get(c);
		if (wayRestrictions.isEmpty() == false){
			long dd = 4;
		}
		return wayRestrictions.contains(w.getId());
	}

	/**
	 * Merges {@code road2} into {@code road1}. This means that
	 * only the way id and the tags of {@code road1} is kept.
	 * For the tag it should not matter because all tags used after the
	 * RoadMerger are compared to be the same.
	 * 
	 * @param road1 first road (will keep the merged road)
	 * @param road2 second road
	 */
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
		
		// merge the POI info
		String wayPOI2 = road2.getWay().getTag(StyledConverter.WAY_POI_NODE_IDS);
		if (wayPOI2 != null){
			String WayPOI1 = road1.getWay().getTag(StyledConverter.WAY_POI_NODE_IDS);
			if (wayPOI2.equals(WayPOI1) == false){
				if (WayPOI1 == null)
					WayPOI1 = "";
				// store combination of both ways. This might contain
				// duplicates, but that is not a problem.
				road1.getWay().addTag(StyledConverter.WAY_POI_NODE_IDS, WayPOI1 + wayPOI2);
			}
		}
		
//		// the mergePoint is now used by one highway less
		mergePoint.decHighwayCount();
		
		// road2 is removed - it must not be part of a restriction
		assert (restrictions.get(endPoint).contains(road2.getWay().getId()) == false);
		
	}

	/**
	 * Merge the roads and copy the results to the given lists.
	 * @param resultingWays list for the merged (and not mergeable) ways
	 * @param resultingGTypes list for the merged (and not mergeable) GTypes
	 */
	public void merge(List<Way> resultingWays, List<GType> resultingGTypes) {

		int noRoadsBeforeMerge = this.roads.size();
		int noMerges = 0;
		List<Road> roadsToMerge = new ArrayList<Road>(this.roads);
		this.roads.clear();
		
		List<Coord> mergePoints = new ArrayList<>();

		// first add all roads with their start and end points to the
		// start/endpoint lists
		for (Road road : roadsToMerge) {
			List<Coord> points = road.getWay().getPoints();
			Coord start = points.get(0);
			Coord end = points.get(points.size() - 1);

			if (start == end) {
				// do not merge closed roads
				roads.add(road);
				continue;
			}
			
			mergePoints.add(start);
			mergePoints.add(end);
			startPoints.add(start, road);
			endPoints.add(end, road);
		}

		// a set of all points where no more merging is possible
		Set<Coord> mergeCompletedPoints = Collections.newSetFromMap(new IdentityHashMap<Coord, Boolean>());
		
		// go through all start/end points and check if a merge is possible
		for (Coord mergePoint : mergePoints) {
			if (mergePoint.isViaNodeOfRestriction()){
				// don't merge at any point that is part of a restriction
				continue;
			}
			if (mergeCompletedPoints.contains(mergePoint)) {
				// a previous run did not show any possible merge
				// do not check again
				continue;
			}
			
			// get all road that start with the merge point
			List<Road> startRoads = startPoints.get(mergePoint);
			// get all roads that end with the merge point
			List<Road> endRoads = endPoints.get(mergePoint);
			
			if (endRoads.isEmpty() || startRoads.isEmpty()) {
				// this might happen if another merge operation changed endPoints and/or startPoints
				mergeCompletedPoints.add(mergePoint);
				continue;
			}
			
			// go through all combinations and test which combination is the best
			double bestAngle = Double.MAX_VALUE;
			Road mergeRoad1 = null;
			Road mergeRoad2 = null;
			
			for (Road road1 : endRoads) {
				List<Coord> points1 = road1.getWay().getPoints();
				
				// go through all candidates to merge
				for (Road road2 : startRoads) {
					List<Coord> points2 = road2.getWay().getPoints();
					
					// the second road is merged into the first road
					// so only the id of the first road is kept
					// This also means that the second road must not have a restriction on 
					// both start and end point
					if (hasRestriction(points2.get(points2.size()-1), road2.getWay())) {
						continue;
					}
					
					// check if both roads can be merged
					if (road1.isMergable(mergePoint, road2)) {
						// yes they might be merged
						// calculate the angle between them 
						// if there is more then one road to merge the one with the lowest angle is merged 
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
			
			// is there a pair of roads that can be merged?
			if (mergeRoad1 != null && mergeRoad2 != null) {
				// yes!! => merge them
				log.debug("Merge",mergeRoad1.getWay().getId(),"and",mergeRoad2.getWay().getId(),"with angle",bestAngle);
				mergeRoads(mergeRoad1, mergeRoad2);
				noMerges++;
			} else {
				// no => do not check again this point again
				mergeCompletedPoints.add(mergePoint);
			}
		}

		// copy all merged roads to the roads list
		for (List<Road> mergedRoads : endPoints.values()) {
			this.roads.addAll(mergedRoads);
		}

		// sort the roads to ensure that the order of roads is constant for two runs
		Collections.sort(this.roads, new Comparator<Road>() {
			public int compare(Road o1, Road o2) {
				return Integer.compare(o1.getIndex(), o2.getIndex());
			}
		});
		
		// copy the roads to the resulting lists
		for (Road r : roads) {
			resultingWays.add(r.getWay());
			resultingGTypes.add(r.getGtype());
		}
		
		// print out some statistics
		int noRoadsAfterMerge = this.roads.size();
		log.info("Roads before/after merge:", noRoadsBeforeMerge, "/",
				noRoadsAfterMerge);
		int percentage = (int) Math.round((noRoadsBeforeMerge - noRoadsAfterMerge) * 100.0d
						/ noRoadsBeforeMerge);
		log.info("Road network reduced by", percentage, "%",noMerges,"merges");
	}
}
