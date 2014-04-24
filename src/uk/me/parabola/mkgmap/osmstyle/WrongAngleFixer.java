/*
 * Copyright (C) 2013-2014.
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

//import java.io.File;
//import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.CoordPOI;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Way;
//import uk.me.parabola.splitter.O5mMapWriter;
import uk.me.parabola.util.GpxCreator;

/**
 * We are rounding coordinates with double precision to map units with a
 * precision of < 2m. This hasn't a big visible effect for single points,
 * but wherever points are connected with lines the lines may show
 * heavy zig-zagging while the original lines were almost straight.
 * This happens when one of the points was rounded to one direction
 * and the next point was rounded to the opposite direction.
 * The effect is esp. visible with parallel roads, rails, and buildings,
 * but also in small roundabouts.
 * The methods in this class try to fix these wrong bearings by
 * moving or removing points.
 *         
 * @author GerdP
 *
 */
public class WrongAngleFixer {
	private static final Logger log = Logger.getLogger(WrongAngleFixer.class);

	static private final double MAX_BEARING_ERROR = 15;
	static private final double MAX_BEARING_ERROR_HALF = MAX_BEARING_ERROR / 2;
	static private final double MAX_DIFF_ANGLE_STRAIGHT_LINE = 3;
	
	private final Area bbox;
	private final String gpxPath = null;
	
	public WrongAngleFixer(Area bbox) {
		this.bbox = bbox;
	}

	/**
	 * Find wrong angles caused by rounding to map units. Try to fix them by
	 * moving, removing or merging points. 
	 * When done, remove obsolete points.  
	 * @param roads list of roads, elements might be set to null by this method
	 * @param lines list of non-routable ways  
	 * @param modifiedRoads Will be enlarged by all roads modified in this method 
	 * @param deletedRoads Will be enlarged by all roads in roads that were set to null by this method 
	 * @param restrictions Map with restriction relations 
	 */
	public void optimizeWays(List<ConvertedWay> roads, List<ConvertedWay> lines, HashMap<Long, ConvertedWay> modifiedRoads, HashSet<Long> deletedRoads, List<RestrictionRelation> restrictions ) {
		printBadAngles("bad_angles_start", roads);
		writeOSM("roads_orig", roads);
		writeOSM("lines_orig", lines);
		removeWrongAngles(roads, lines, modifiedRoads, deletedRoads, restrictions);
		writeOSM("roads_post_rem_wrong_angles", roads);
		removeObsoletePoints(roads, modifiedRoads);
		writeOSM("roads_post_rem_obsolete_points", roads);
		printBadAngles("bad_angles_finish", roads);
		removeObsoletePoints(lines, modifiedRoads);
		writeOSM("lines_final", lines);
	}	
	
	private static void replaceCoord(Coord toRepl, Coord replacement, Map<Coord, Coord> replacements) {
		assert toRepl != replacement;
		if (toRepl.getOnBoundary()){
			if (replacement.equals(toRepl) == false){
				log.error("boundary node is replaced by node with non-equal coordinates at", toRepl.toOSMURL());
				assert false : "boundary node is replaced" ;
			}
			replacement.setOnBoundary(true);
		}
		toRepl.setReplaced(true);
		if (toRepl instanceof CoordPOI && ((CoordPOI) toRepl).isUsed()) {
			replacement = new CoordPOI(replacement);
			((CoordPOI) replacement).setNode(((CoordPOI) toRepl).getNode());
		}
		if (toRepl.isViaNodeOfRestriction())
			replacement.setViaNodeOfRestriction(true);
		replacements.put(toRepl, replacement);
		while (toRepl.getHighwayCount() > replacement.getHighwayCount())
			replacement.incHighwayCount();
		if (toRepl.isEndOfWay() ){
			replacement.setEndOfWay(true);
		}
	}
	
	/**
	 * Common code to handle replacements of points in ways. Checks for special 
	 * cases regarding CoordPOI.
	 * 
	 * @param p point to replace
	 * @param way way that contains p
	 * @param replacements the Map containing the replaced points
	 * @return the replacement
	 */
	private static Coord getReplacement(Coord p, Way way,
			Map<Coord, Coord> replacements) {
		// check if this point is to be replaced because
		// it was previously merged into another point
		if (p.isReplaced()) {
			Coord replacement = null;
			Coord r = p;
			while ((r = replacements.get(r)) != null) {
				replacement = r;
			}

			if (replacement != null) {
				if (p instanceof CoordPOI) {
					CoordPOI cp = (CoordPOI) p;
					Node node = cp.getNode();
					if (cp.isUsed() && way != null && way.getId() != 0) {
						String wayPOI = way.getTag(StyledConverter.WAY_POI_NODE_IDS);
						if (wayPOI != null && wayPOI.contains("["+node.getId()+"]")){
							if (replacement instanceof CoordPOI) {
								Node rNode = ((CoordPOI) replacement).getNode();
								if (rNode.getId() != node.getId()) {
									if (wayPOI.contains("["+ rNode.getId() + "]")){
										log.warn("CoordPOI", node.getId(),
												"replaced by CoordPOI",
												rNode.getId(), "in way",
												way.toBrowseURL());
									}
									else
										log.warn("CoordPOI", node.getId(),
												"replaced by ignored CoordPOI",
												rNode.getId(), "in way",
												way.toBrowseURL());
								}
							} else
								log.warn("CoordPOI", node.getId(),
										"replaced by simple coord in way",
										way.toBrowseURL());
						}
					}
				}
				return replacement;
			} 
			log.error("replacement not found for point " + p.toOSMURL());
			
		}
		return p;
	}

	/**
	 * Find wrong angles caused by rounding to map units. Try to fix them by
	 * moving, removing or merging points.
	 * @param roads list with routable ways or null, if lines should be optimized
	 * @param lines list with non-routable ways
	 * @param modifiedRoads map of modified routable ways (modified by this routine)
	 * @param deletedRoads set of ids of deleted routable ways (modified by this routine)
	 * @param restrictions Map with restriction relations. The restriction relations may be modified by this routine 
	 */
	private void removeWrongAngles(List<ConvertedWay> roads, List<ConvertedWay> lines, HashMap<Long, ConvertedWay> modifiedRoads, HashSet<Long> deletedRoads, List<RestrictionRelation> restrictions) {
		// replacements maps those nodes that have been replaced to
		// the node that replaces them
		Map<Coord, Coord> replacements = new IdentityHashMap<>();

		final HashSet<Coord> changedPlaces = new HashSet<>();
		int numNodesMerged = 0; 
		HashSet<Way> waysWithBearingErrors = new HashSet<>();
		HashSet<Long> waysThatMapToOnePoint = new HashSet<>();
		int pass = 0;
		Way lastWay = null;
		HashSet<Long> allRoads = new HashSet<>();
		for (ConvertedWay cw : roads)
			allRoads.add(cw.getWay().getId());
		List<ConvertedWay> convertedWays = new ArrayList<>(roads.size() + lines.size());
		convertedWays.addAll(roads);
		for (ConvertedWay cw : lines){
			if (allRoads.contains(cw.getWay().getId()) == false)
				convertedWays.add(cw);
		}
		allRoads = null;
		
		boolean anotherPassRequired = true;
		while (anotherPassRequired && pass < 20) {
			anotherPassRequired = false;
			log.info("Removing wrong angles - PASS " + ++pass);
			writeOSM("lines_pass_" + pass, convertedWays);	
			// Step 1: detect points which are parts of line segments with wrong bearings
			lastWay = null;
			for (ConvertedWay cw : convertedWays) {
				if (!cw.isValid()) 
					continue;
				Way way = cw.getWay();
				if (way.equals(lastWay)) 
					continue;
				if (pass != 1 && waysWithBearingErrors.contains(way) == false)
					continue;
				lastWay = way;
				List<Coord> points = way.getPoints();
				
				// scan through the way's points looking for line segments with big 
				// bearing errors 
				Coord prev = null;
				if (points.get(0) == points.get(points.size()-1) && points.size() >= 2)
					prev = points.get(points.size()-2);
				boolean hasNonEqualPoints = false;
				for (int i = 0; i < points.size(); ++i) {
					Coord p = points.get(i);
					if (pass == 1)
						p.setRemove(false);
					p = getReplacement(p, way, replacements);
					if (i == 0 || i == points.size()-1){
						p.setEndOfWay(true);
					}
					
					if (prev != null) {
						if (pass == 1 && p.equals(prev) == false)
							hasNonEqualPoints = true;
						double err = calcBearingError(p,prev);
						if (err >= MAX_BEARING_ERROR){
							// bearing error is big
							p.setPartOfBadAngle(true);
							prev.setPartOfBadAngle(true);
						}
					}
					prev = p;
				}
				if (pass == 1 && hasNonEqualPoints == false){
					waysThatMapToOnePoint.add(way.getId());
					log.info("all points of way",way.toBrowseURL(),"are rounded to equal map units" );
				}
			}
			// Step 2: collect the line segments that are connected to critical points
			IdentityHashMap<Coord, CenterOfAngle> centerMap = new IdentityHashMap<>();
			List<CenterOfAngle> centers = new ArrayList<>(); // needed for ordered processing
			int centerId = 0;
				
			lastWay = null;
			for (ConvertedWay cw : convertedWays) {
				if (!cw.isValid()) 
					continue;
				Way way = cw.getWay();
				if (way.equals(lastWay)) 
					continue;
				if (pass != 1 && waysWithBearingErrors.contains(way) == false)
					continue;
				lastWay = way;

				boolean wayHasSpecialPoints = false;
				List<Coord> points = way.getPoints();
				// scan through the way's points looking for line segments with big 
				// bearing errors
				Coord prev = null;
				if (points.get(0) == points.get(points.size()-1) && points.size() >= 2)
					prev = points.get(points.size()-2);
				
				for (int i = 0; i < points.size(); ++i) {
					Coord p = points.get(i);
					if (prev != null) {
						if (p == prev){
							points.remove(i);
							--i;
							if (cw.isRoad())
								modifiedRoads.put(way.getId(), cw);
							continue;
						}
						if (p.isPartOfBadAngle() || prev.isPartOfBadAngle()) {
							wayHasSpecialPoints = true;
							// save both points with their neighbour
							Coord p1 = prev;
							Coord p2 = p;
							CenterOfAngle coa1 = centerMap.get(p);
							if (coa1 == null) {
								coa1 = new CenterOfAngle(p, centerId++);
								centerMap.put(p, coa1);
								centers.add(coa1);
							}
							CenterOfAngle coa2 = centerMap.get(prev);
							if (coa2 == null) {
								coa2 = new CenterOfAngle(prev, centerId++);
								centerMap.put(prev, coa2);
								centers.add(coa2);
							}
							coa1.addNeighbour(coa2);
							coa2.addNeighbour(coa1);
							if (points.size() == 2) {
								// way has only two points, don't merge them
								coa1.addBadMergeCandidate(coa2);
							}
							if (cw.isRoad()){
								if (p1.getHighwayCount() >= 2 && p2.getHighwayCount() >= 2){
									if (cw.isRoundabout()) {
										// avoid to merge exits of roundabouts
										coa1.addBadMergeCandidate(coa2);
									}
								}
							}
						}
					}
					prev = p;
				}
				if (pass == 1 && wayHasSpecialPoints)
					waysWithBearingErrors.add(way);
			}
			// Step 3: Update list of ways with bearing errors or points next to them 
			lastWay = null;
			for (ConvertedWay cw : convertedWays) {
				if (!cw.isValid()) 
					continue;
				Way way = cw.getWay();
				if (way.equals(lastWay)) 
					continue;
				lastWay = way;
				if (waysWithBearingErrors.contains(way))
					continue;
				List<Coord> points = way.getPoints();
				// scan through the way's points looking for line segments with big 
				// bearing errors 
				for (Coord p: points) {
					if (p.getHighwayCount() < 2)
						continue;
					if (centerMap.containsKey(p)){
						waysWithBearingErrors.add(way);
						break;
					}
				}
			}
			log.info("pass " + pass + ": analysing " + centers.size() + " points with bearing problems.");
			centerMap = null; // Return to GC 
			// Step 4: try to correct the errors
			List<CenterOfAngle> checkAgainList = null;
			boolean tryMerge = false;
			while (true){
				checkAgainList = new ArrayList<>();
				for (CenterOfAngle coa : centers) {
					coa.center.setPartOfBadAngle(false); // reset flag for next pass
					if (coa.getCurrentLocation(replacements) == null)
						continue; // removed center
					if (coa.isOK(replacements) == false) {
						boolean changed = coa.tryChange(replacements, tryMerge);
						if (changed){
							if (gpxPath != null)
								changedPlaces.add(coa.center);
							continue;
						}
						checkAgainList.add(coa);
					}
				}
				if (tryMerge)
					break; // leave when 2nd pass finished
				tryMerge = true;
				centers = checkAgainList;
			}
			
			// Step 5: apply the calculated corrections to the ways
			lastWay = null;
			boolean lastWayModified = false;
			for (ConvertedWay cw : convertedWays) {
				if (!cw.isValid()) 
					continue;
				Way way = cw.getWay();
				if (waysWithBearingErrors.contains(way) == false)
					continue;
				List<Coord> points = way.getPoints();
				if (way.equals(lastWay)) {
					if (lastWayModified){
						points.clear();
						points.addAll(lastWay.getPoints());
					}
					continue;
				}
				lastWay = way;
				lastWayModified = false;
				// loop backwards because we may delete points
				for (int i = points.size() - 1; i >= 0; i--) {
					Coord p = points.get(i);
					if (p.isToRemove()) {
						points.remove(i);
						anotherPassRequired = true;
						lastWayModified = true;
						if (i > 0 && i < points.size()) {
							// special case: handle micro loop
							if (points.get(i - 1) == points.get(i))
								points.remove(i);
							}
						continue;
					}
					// check if this point is to be replaced because
					// it was previously moved
					Coord replacement = getReplacement(p, way, replacements);
					if (p == replacement) 
						continue;
					
					if (p.isViaNodeOfRestriction()){
						// make sure that we find the restriction with the new coord instance
						replacement.setViaNodeOfRestriction(true);
						p.setViaNodeOfRestriction(false);
					}
					p = replacement;
					// replace point in way
					points.set(i, p);
					if (p.getHighwayCount() >= 2)
						numNodesMerged++;
					lastWayModified = true;
					if (i + 1 < points.size() && points.get(i + 1) == p) {
						points.remove(i);
						anotherPassRequired = true;
					}
					if (i -1 >= 0 && points.get(i-1) == p){
						points.remove(i);
						anotherPassRequired = true;
					}
				}
				if (lastWayModified && cw.isRoad()){
					modifiedRoads.put(way.getId(), cw);
				}
			}
		}
		// finish: remove remaining duplicate points
		int numWaysDeleted = 0;
		lastWay = null;
		boolean lastWayModified = false;
		for (ConvertedWay cw : convertedWays) {
			Way way = cw.getWay();
			List<Coord> points = way.getPoints();
			if (points.size() < 2) {
				if (log.isInfoEnabled())
					log.info("  Way " + way.getTag("name") + " (" + way.toBrowseURL() + ") has less than 2 points - deleting it");

				if (cw.isRoad())
					deletedRoads.add(way.getId());
				else {
					if (waysThatMapToOnePoint.contains(way.getId()) == false)
						log.warn("non-routable way " ,way.getId(),"was removed");
				}
				++numWaysDeleted;
				continue;
			}
			if (way.equals(lastWay)) {
				if (lastWayModified){
					points.clear();
					points.addAll(lastWay.getPoints());
				}
				continue;
			}
			lastWay = way;
			lastWayModified = false;
			Coord prev = points.get(points.size() - 1);
			// loop backwards because we may delete points
			for (int i = points.size() - 2; i >= 0; i--) {
				Coord p = points.get(i);
				if (p == prev){
					points.remove(i);
					lastWayModified = true;
				}
//				if (p.equals(prev) && (p.getHighwayCount() < 2 || prev.getHighwayCount() < 2)){
//					// not an error, but should not happen
//					log.warn("way " + way.getId() + " still has consecutive equal points at " + p.toOSMURL()); 
//				}
				prev = p;
			}
		}
		for (RestrictionRelation rr: restrictions){
			for (Coord p: rr.getViaCoords()){ 
				Coord replacement = getReplacement(p, null, replacements);
				if (p != replacement){
					rr.replaceViaCoord(p, replacement);
				}
			}
		}
		
		if (gpxPath != null) {
			GpxCreator.createGpx(gpxPath + "solved_badAngles", bbox.toCoords(),
					new ArrayList<>(changedPlaces));
		}
		if (anotherPassRequired)
			log.error("Removing wrong angles - didn't finish in " + pass + " passes, giving up!");
		else
			log.info("Removing wrong angles - finished in", pass, "passes (", numNodesMerged, "nodes merged,", numWaysDeleted, "ways deleted)"); 		
	}

	
	/** 
	 * remove obsolete points in ways. Obsolete are points which are
	 * very close to 180 degrees angles in the real line or wrong points. 
	 * Wrong points are those that produce wrong angles, so that  
	 * removing them reduces the error.
	 * @param convertedWays 
	 * @param modifiedRoads 
	 */
	private void removeObsoletePoints(List<ConvertedWay> convertedWays, HashMap<Long, ConvertedWay> modifiedRoads){
		Way lastWay = null;
		int numPointsRemoved = 0;
		boolean lastWasModified = false;
		List<Coord> removedInWay = new ArrayList<>();
		List<Coord> obsoletePoints = new ArrayList<>();
		List<Coord> modifiedPoints = new ArrayList<>();
		for (ConvertedWay cw : convertedWays) {
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if (cw.isRoad() == false && modifiedRoads.containsKey(way.getId()))
				continue;
			if (way.equals(lastWay)) {
				if (lastWasModified){
					way.getPoints().clear();
					way.getPoints().addAll(lastWay.getPoints());
				}
				continue;
			}
			lastWay = way;
			lastWasModified = false;
			List<Coord> points = way.getPoints();
			modifiedPoints.clear();
			double maxErrorDistance = calcMaxErrorDistance(points.get(0));
			boolean draw = false;
			removedInWay.clear();
			modifiedPoints.add(points.get(0));
			// scan through the way's points looking for points which are
			// on almost straight line and therefore obsolete
			for (int i = 1; i+1 < points.size(); i++) {
				Coord cm = points.get(i);
				if (allowedToRemoveIgnoreEndpoints(cm) == false){
					modifiedPoints.add(cm);
					continue;
				}
				Coord c1 = modifiedPoints.get(modifiedPoints.size()-1);
				Coord c2 = points.get(i+1);
				if (c1 == c2){
					// loop, handled by split routine
					modifiedPoints.add(cm);
					continue; 
				}
				
				boolean keepThis = true;
				double realAngle = Utils.getAngle(c1, cm, c2);
				if (Math.abs(realAngle) < MAX_DIFF_ANGLE_STRAIGHT_LINE){ 
					double distance = distToLineHeron(cm, c1, c2);
					if (distance >= maxErrorDistance){
						modifiedPoints.add(cm);
						continue;
					}
					keepThis = false;
				} else {
					double displayedAngle = Utils.getDisplayedAngle(c1, cm, c2);
					if (Math.signum(displayedAngle) != Math.signum(realAngle)){
						// straight line is closer to real angle 
						keepThis = false;
					} else if (Math.abs(displayedAngle) < 1){ 
						// displayed line is nearly straight
						if (c1.getHighwayCount() < 2 && c2.getHighwayCount() < 2){
							// we can remove the point
							keepThis = false;
						}
					} else if (Math.abs(realAngle-displayedAngle) > 2 * Math.abs(realAngle) && Math.abs(realAngle) < MAX_BEARING_ERROR_HALF){
						// displayed angle is much sharper than wanted, straight line is closer to real angle
						keepThis = false;
					} 
				}
				if (keepThis){
					modifiedPoints.add(cm);
					continue;
				}
				if (log.isDebugEnabled())
					log.debug("removing obsolete point on almost straight segment in way ",way.toBrowseURL(),"at",cm.toOSMURL());
				if (gpxPath != null){
					obsoletePoints.add(cm);
					removedInWay.add(cm);
				}
				numPointsRemoved++;
				lastWasModified = true;
				
			}
			if (lastWasModified){
				modifiedPoints.add(points.get(points.size()-1));
				points.clear();
				points.addAll(modifiedPoints);
				if (cw.isRoad())
					modifiedRoads.put(way.getId(), cw);
				if (gpxPath != null){
					if (draw || cw.isRoundabout()) {
						GpxCreator.createGpx(gpxPath+way.getId()+"_dpmod", points,removedInWay);
					}
				}
			}
		}
		if (gpxPath != null){
			GpxCreator.createGpx(gpxPath + "obsolete", bbox.toCoords(),
					new ArrayList<>(obsoletePoints));
			
		}
		log.info("Removed", numPointsRemoved, "obsolete points in lines"); 
	}
	
	/** 
	 * debug code
	 * @param roads 
	 */
	private void printBadAngles(String name, List<ConvertedWay> roads){
		if (gpxPath ==  null)
			return;
		List<ConvertedWay> badWays = new ArrayList<>();
		Way lastWay = null;
		List<Coord> badAngles = new ArrayList<>();
		for (int w = 0; w < roads.size(); w++) {
			ConvertedWay cw = roads.get(w);
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if (way.equals(lastWay)) {
				continue;
			}
			boolean hasBadAngles = false;
			lastWay = way;
			List<Coord> points = way.getPoints();
			// scan through the way's points looking for points which are
			// on almost straight line and therefore obsolete
			for (int i = points.size() - 2; i >= 1; --i) {
				Coord cm = points.get(i);
				Coord c1 = points.get(i-1);
				Coord c2 = points.get(i+1);
				if (c1 == c2){
					// loop, handled by split routine
					continue; 
				}
				double realAngle = Utils.getAngle(c1, cm, c2);
				double displayedAngle = Utils.getDisplayedAngle(c1, cm, c2);
				if (Math.abs(displayedAngle-realAngle) > 30){
					badAngles.add(cm);
					hasBadAngles = true;
					//						badAngles.addAll(cm.getAlternativePositions());
				}

			}
			if (points.size() > 2){
				Coord p0 = points.get(0);
				Coord plast = points.get(points.size()-1);
				if (p0 == plast){
					Coord cm = points.get(0);
					Coord c1 = points.get(points.size()-2);
					Coord c2 = points.get(1);
					if (c1 == c2){
						// loop, handled by split routine
						continue; 
					}
					double realAngle = Utils.getAngle(c1, cm, c2);
					double displayedAngle = Utils.getDisplayedAngle(c1, cm, c2);
					if (Math.abs(displayedAngle-realAngle) > 30){
						badAngles.add(cm);
						hasBadAngles = true;
						//						badAngles.addAll(cm.getAlternativePositions());
					}
				}
			}
			if (hasBadAngles)
				badWays.add(cw);
		}
		GpxCreator.createGpx(gpxPath + name, bbox.toCoords(),
				new ArrayList<>(badAngles));
		writeOSM(name, badWays);
	}
	
	/**
	 * Check if the point can safely be removed from a road. 
	 * @param p
	 * @return true if remove is okay
	 */
	private static boolean allowedToRemove(Coord p){
		if (p.isEndOfWay())
			return false;
		return allowedToRemoveIgnoreEndpoints(p);
	}
	
	/**
	 * Check if the point can safely be removed from a road. 
	 * @param p
	 * @return true if remove is okay
	 */
	private static boolean allowedToRemoveIgnoreEndpoints(Coord p){
		if (p instanceof CoordPOI){
			if (((CoordPOI) p).isUsed()){
				return false;
			}
		}
		if (p.getHighwayCount() >= 2 || p.getOnBoundary() || p.isViaNodeOfRestriction()) {
			return false;
		}
		return true;
	}
	
	/**
	 * helper class
	 */
	private class CenterOfAngle {
		final Coord center;
		final List<CenterOfAngle> neighbours;
		final int id; // debugging aid
		boolean wasMerged;
		
		List<CenterOfAngle> badMergeCandidates;
		
		public CenterOfAngle(Coord center, int id) {
			this.center = center;
			assert center.isReplaced() == false;
			this.id = id;
			neighbours = new ArrayList<>();
		}

		
		@Override
		public String toString() {
			return "CenterOfAngle [id=" + id + ", wasMerged=" + wasMerged + ", num Neighbours="+neighbours.size()+"]";
		}


		@Override
		public int hashCode() {
			return center.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			return center == ((CenterOfAngle) obj).center;
		}

		/**
		 * returns current center position or null if removed
		 * @param replacements
		 * @return
		 */
		public Coord getCurrentLocation(Map<Coord, Coord> replacements){
			Coord c = getReplacement(center, null, replacements); 
			if (c.isToRemove())
				return null;
			return c; 
		}
		
		/**
		 * Add neighbour which should not be merged
		 * @param other
		 */
		public void addBadMergeCandidate(CenterOfAngle other) {
			if (badMergeCandidates == null)
				badMergeCandidates = new ArrayList<>();
			badMergeCandidates.add(other);
		}

		public void addNeighbour(CenterOfAngle other) {
			if (this == other){
				log.error("neighbour is equal" );
			}
			boolean isNew = true;
			// we want only different Coord instances here
			for (CenterOfAngle neighbour : neighbours) {
				if (neighbour == other) {
					isNew = false;
					break;
				}
			}
			if (isNew)
				neighbours.add(other);
		}

		/**
		 * 
		 * @param replacements
		 * @return false if this needs changes 
		 */
		public boolean isOK(Map<Coord, Coord> replacements) {
			Coord c = getCurrentLocation (replacements);
			if (c == null)
				return true; // removed center: nothing to do
			for (CenterOfAngle neighbour : neighbours) {
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					continue; // skip removed neighbours
				double err = calcBearingError(c, n);
				if (err >= MAX_BEARING_ERROR)
					return false;
			}
			return true;
		}
		
		/**
		 * Try whether a move or remove or merge of this centre
		 * fixes bearing problems.
		 * @param replacements
		 * @param tryAlsoMerge true means merge is allowed
		 * @return true if something was changed
		 */
		public boolean tryChange(Map<Coord, Coord> replacements, boolean tryAlsoMerge) {
			if (wasMerged ) {
				return false;
			}
			Coord currentCenter = getCurrentLocation(replacements);
			if (currentCenter == null)
				return false; // cannot modify removed centre
			CenterOfAngle worstNeighbour = null;
			Coord worstNP = null;
			double initialMaxError = 0;
			double initialSumErr = 0;
			for (CenterOfAngle neighbour : neighbours) {
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					return false; // neighbour was removed
				if (currentCenter.highPrecEquals(n)){
					if (currentCenter == n){
						log.error(id + ": bad neighbour " + neighbour.id + " zero distance");
					}
					if (badMergeCandidates != null && badMergeCandidates.contains(neighbour )
							|| neighbour.badMergeCandidates != null && neighbour.badMergeCandidates.contains(this)) {
						//not allowed to merge
					} else {
						replaceCoord(currentCenter, n, replacements);
						neighbour.wasMerged = wasMerged = true;
						return true;
					}
				}
				double err = calcBearingError(currentCenter, n);
				if (err != Double.MAX_VALUE)
					initialSumErr += err;
				if (err > initialMaxError){
					initialMaxError = err;
					worstNeighbour = neighbour;
					worstNP = n;
				}
			}
			if (initialMaxError < MAX_BEARING_ERROR)
				return false;
			double removeErr = calcRemoveError(replacements);
			if (removeErr == 0){
//				createGPX(gpxPath+id+"_rem_0", replacements);
				currentCenter.setRemove(true);
				return true;
			}
			if (initialMaxError == Double.MAX_VALUE)
				initialSumErr = initialMaxError;
			double bestReplErr = initialMaxError; 
			Coord bestCenterReplacement = null;
			List<Coord> altPositions = currentCenter.getAlternativePositions();
			for (Coord altCenter : altPositions){
				double err = calcBearingError(altCenter, worstNP);
				if (err >= bestReplErr)
					continue;
				// alt. position is improvement, check all neighbours
				err = calcMaxError(replacements, currentCenter, altCenter);
				if (err >= initialMaxError)
					continue;
				bestReplErr = err;
				bestCenterReplacement = altCenter;
			}
			Coord bestNeighbourReplacement = null;
			if (worstNP.hasAlternativePos()){
				for (Coord altCenter : altPositions){
					replaceCoord(currentCenter, altCenter, replacements);
					for (Coord altN: worstNP.getAlternativePositions()){
						double err = calcBearingError(altCenter, altN);
						if (err >= bestReplErr)
							continue;
						double errNeighbour = worstNeighbour.calcMaxError(replacements, worstNP, altN);
						if (errNeighbour >= bestReplErr)
							continue;
						bestReplErr = err;
						bestCenterReplacement = altCenter;
						bestNeighbourReplacement = altN;
					}
					replacements.remove(currentCenter);
					currentCenter.setReplaced(false);
				}
			}
			
			if (bestReplErr < MAX_BEARING_ERROR){
//				String msg = "_good"; 
				if (removeErr < bestReplErr && initialMaxError - removeErr >= MAX_BEARING_ERROR_HALF && removeErr < MAX_BEARING_ERROR_HALF){
					bestCenterReplacement = null;
//					createGPX(gpxPath+id+"_rem_pref", replacements);
				} else if (initialMaxError - bestReplErr < MAX_BEARING_ERROR_HALF || bestReplErr > MAX_BEARING_ERROR_HALF){
//					msg = "_rather_good";
				}
				if (bestCenterReplacement != null){
					replaceCoord(currentCenter, bestCenterReplacement, replacements);
					if (bestNeighbourReplacement != null)
						replaceCoord(worstNP, bestNeighbourReplacement, replacements);
					double modifiedSumErr = calcSumOfErrors(replacements);
					if (modifiedSumErr < initialSumErr){
//						if ("_good".equals(msg) == false)
//							createGPX(gpxPath+id+msg, replacements);
						if (bestNeighbourReplacement != null){
//							worstNeighbour.createGPX(gpxPath+worstNeighbour.id+msg+"_n", replacements);
						}
						return true;
					}
					// revert changes
//					System.out.println("ignoring possible improvement at center " + id + " " + initialMaxError + " -> " + bestReplErr + " " + initialSumErr + " --> " + modifiedSumErr);
//					createGPX(gpxPath+id+"_reverted_"+msg, replacements);
					replacements.remove(currentCenter);
					currentCenter.setReplaced(false);
					replacements.remove(worstNP);
					worstNP.setReplaced(false);
//					createGPX(gpxPath+id+"_as_is", replacements);
					bestCenterReplacement = null;
				}
			}
			if (removeErr < MAX_BEARING_ERROR){
//				createGPX(gpxPath+id+"_rem", replacements);
				currentCenter.setRemove(true);
				return true;
			}
			if (!tryAlsoMerge)
				return false;
			
			double dist = currentCenter.distance(worstNP);
			double maxDist = calcMaxErrorDistance(currentCenter) * 2;
			boolean forceMerge = dist < maxDist || currentCenter.equals(worstNP);
			if (forceMerge || this.neighbours.size() == 3 && worstNeighbour.neighbours.size() == 3)
				return tryMerge(forceMerge, initialMaxError, worstNeighbour, replacements);
			if (bestCenterReplacement != null){
				double replImprovement = initialMaxError - bestReplErr;
				if (replImprovement < MAX_BEARING_ERROR)
					return false;
				replaceCoord(currentCenter, bestCenterReplacement, replacements);
				if (bestNeighbourReplacement != null){
					replaceCoord(worstNP, bestNeighbourReplacement, replacements);
				}
				double modifiedSumErr = calcSumOfErrors(replacements);
				if (modifiedSumErr < initialSumErr){
//					System.out.println("ignoring possible improvement at center " + id + " " + initialMaxError + " -> " + bestReplErr + " " + initialSumErr + " --> " + modifiedSumErr);
//					createGPX(gpxPath+id+"_possible", replacements);
				}
				replacements.remove(currentCenter);
				currentCenter.setReplaced(false);
				if (bestNeighbourReplacement != null){
					replacements.remove(worstNP);
					worstNP.setReplaced(false);
				}
				if (modifiedSumErr < initialSumErr){
//					createGPX(gpxPath+id+"_as_is", replacements);
				}
			}
			return false;
		}

		/**
		 * Calculate error when two centres are merged. If they are not equal 
		 * and the error is too big, nothing is changed and false is returned. 
		 * 
		 * @param forceMerge true: skip straight line check
		 * @param initialMaxError max. bearing error of this centre
		 * @param neighbour neighbour to merge 
		 * @param replacements
		 * @return true if merge is okay
		 */
		private boolean tryMerge(boolean forceMerge, double initialMaxError, CenterOfAngle neighbour, Map<Coord, Coord> replacements) {
			if (badMergeCandidates != null && badMergeCandidates.contains(neighbour )
					|| neighbour.badMergeCandidates != null && neighbour.badMergeCandidates.contains(this)) {
				return false; // not allowed to merge
			}
			Coord c = getCurrentLocation(replacements);
			Coord n = neighbour.getCurrentLocation(replacements);
			// check special cases: don't merge if
			// 1) both points are via nodes 
			// 2) both nodes are boundary nodes with non-equal coords
			// 3) on point is via node and the other is a boundary node, the result could be that the restriction is ignored 
			if (c.getOnBoundary()){
				if (n.isViaNodeOfRestriction() || n.getOnBoundary() && c.equals(n) == false)
					return false; 
			}
			if (c.isViaNodeOfRestriction() && (n.isViaNodeOfRestriction() || n.getOnBoundary()))
				 return false;
			 
			Coord mergePoint;
			if (c.getOnBoundary())
				mergePoint = c;
			else if (n.getOnBoundary())
				mergePoint = n;
			else if (c.equals(n))
				mergePoint = c;
			else 
				mergePoint = c.makeBetweenPoint(n, 0.5);
			double err = 0;
			if (c.equals(n) == false){
				err = calcMergeErr(neighbour, mergePoint, replacements);
				if (err == Double.MAX_VALUE && initialMaxError == Double.MAX_VALUE){
					log.warn("still equal neighbour after merge",c.toOSMURL());
				} else { 
					if (err >= MAX_BEARING_ERROR)
						return false;
					if (initialMaxError - err < MAX_BEARING_ERROR_HALF && err > MAX_BEARING_ERROR_HALF){
						return false; // improvement too small
					}
				}
			}
			if (!forceMerge){
				// merge only if the merged line is part of a (nearly) straight line going through both centres,
				if (!checkNearlyStraight(c.bearingTo(n), neighbour, replacements)
						|| !neighbour.checkNearlyStraight(n.bearingTo(c), this, replacements)) {
//					createGPX(gpxPath + "no_more_merge_" + id, replacements);
//					neighbour.createGPX(gpxPath + "no_more_merge_" + neighbour.id, replacements);
//					System.out.println("no_merge at " + mergePoint.toDegreeString() + " " + mergePoint.toOSMURL() + " at " + id);
					return false;
				}
			}
			int hwc = c.getHighwayCount() + n.getHighwayCount() - 1;
			for (int i = 0; i < hwc; i++)
				mergePoint.incHighwayCount();
			if (c != mergePoint)
				replaceCoord(c, mergePoint, replacements);
			if (n != mergePoint){
				replaceCoord(n, mergePoint, replacements);
			}
//			createGPX(gpxPath+id+"_merged", replacements);
//			neighbour.createGPX(gpxPath+neighbour.id+"_merged_w_"+id, replacements);
			neighbour.wasMerged = wasMerged = true;
			return true;
		}

		/**
		 * Try to find a line that builds a nearly straight line
		 * with the connection to an other centre. 
		 * @param bearing bearing of the connection to the other centre
		 * @param other the other centre
		 * @param replacements 
		 * @return true if a nearly straight line exists
		 */
		private boolean checkNearlyStraight(double bearing, CenterOfAngle other,
				Map<Coord, Coord> replacements) {
			Coord c = getCurrentLocation(replacements);
			for (CenterOfAngle neighbour : neighbours) {
				if (neighbour == other) 
					continue;
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					continue;
				double bearing2 = c.bearingTo(n);
				double angle = bearing2 - (bearing - 180);
				while(angle > 180)
					angle -= 360;
				while(angle < -180)
					angle += 360;
				if (Math.abs(angle) < 10) // tolerate small angle
					return true;
			}
			return false;
		}

		/**
		 * Calculate max. error of this merged with other centres. 
		 * @param other the other centre
		 * @param mergePoint the point which should be used as a new centre for both
		 * @param replacements
		 * @return the error
		 */
		private double calcMergeErr(CenterOfAngle other, Coord mergePoint, Map<Coord, Coord> replacements) {
			double maxErr = 0;
			for (CenterOfAngle neighbour : neighbours) {
				if (neighbour == other) 
					continue;
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n != null){
					double err = calcBearingError(mergePoint, n);
					if (err > maxErr)
						maxErr = err;
				}
			}
			for (CenterOfAngle othersNeighbour : other.neighbours) {
				if (othersNeighbour == this) 
					continue;
				Coord n = othersNeighbour.getCurrentLocation(replacements);
				if (n != null){
					double err = calcBearingError(mergePoint, n);
					if (err > maxErr)
						maxErr = err;
				}
			}
			return maxErr;
		}

		/**
		 * Calculate max. bearing error of centre point to all neighbours.
		 * @param replacements
		 * @param toRepl if centre or a neighbour center is identical to this, use replacement instead
		 * @param replacement see toRepl
		 * @return error [0..180] or Double.MAX_VALUE in case of equal points
		 */
		private double calcMaxError(Map<Coord, Coord> replacements,
				Coord toRepl, Coord replacement) {
			double maxErr = 0;
			Coord c = getCurrentLocation(replacements);
			for (CenterOfAngle neighbour : neighbours) {
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					continue; // neighbour was removed
				double err;
				if (c == toRepl)
					err = calcBearingError(replacement, n);
				else if (n == toRepl)
					err = calcBearingError(c, replacement);
				else 
					err = calcBearingError(c, n);
				if (err == Double.MAX_VALUE)
					return err;
				if (err > maxErr)
					maxErr = err;
			}
			return maxErr;
		}

		/**
		 * Calculate sum of errors for a centre.
		 * @param replacements
		 * @return
		 */
		private double calcSumOfErrors(Map<Coord, Coord> replacements) {
			double SumErr = 0;
			Coord c = getCurrentLocation(replacements);
			for (CenterOfAngle neighbour : neighbours) {
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					continue; // skip removed neighbour
				double err = calcBearingError(c, n);
				if (err == Double.MAX_VALUE)
					return err;
				SumErr += err;
			}
			return SumErr;
		}

		/**
		 * Calculate error for a removed centre.
		 * @param replacements
		 * @return Double.MAX_VALUE if centre must not be deleted, else [0..180]
		 */
		private double calcRemoveError(Map<Coord, Coord> replacements) {
			if (allowedToRemove(center) == false)
				return Double.MAX_VALUE;
			Coord c = getCurrentLocation(replacements);
			if (neighbours.size() > 2)
				return Double.MAX_VALUE;
			Coord[] outerPoints = new Coord[neighbours.size()];
			
			for (int i = 0; i < neighbours.size(); i++) {
				CenterOfAngle neighbour = neighbours.get(i);
				Coord n = neighbour.getCurrentLocation(replacements);
				if (n == null)
					return Double.MAX_VALUE;
				if (c.equals(n)){
					if (c.getDistToDisplayedPoint() < n.getDistToDisplayedPoint())
						return 0;
				}
				outerPoints[i] = n;
			}
			if (neighbours.size() < 2)
				return Double.MAX_VALUE;
			if (c.getDistToDisplayedPoint() < Math.max(outerPoints[0].getDistToDisplayedPoint(), outerPoints[1].getDistToDisplayedPoint()))
				return Double.MAX_VALUE;
			double dsplAngle = Utils.getDisplayedAngle(outerPoints[0], c, outerPoints[1]);
			if (Math.abs( dsplAngle ) < 3)
				return Double.MAX_VALUE;
			double realAngle = Utils.getAngle(outerPoints[0], c, outerPoints[1]);
			double err = Math.abs(realAngle) / 2;
			return err;
		}

		// TODO: remove this debugging aid
		private void createGPX(String gpxName, Map<Coord, Coord> replacements) {
			if (gpxName == null || gpxPath == null)
				return;
			if (gpxName.isEmpty())
				gpxName = gpxPath + id + "_no_info";
			// print lines after change
			Coord c = getReplacement(center, null, replacements);
			List<Coord> alternatives = c.getAlternativePositions();
			for (int i = 0; i < neighbours.size(); i++) {
				CenterOfAngle n = neighbours.get(i);
				Coord nc = getReplacement(n.center, null, replacements);
				if (nc == null)
					continue; // skip removed neighbour
				if (i == 0 && alternatives.isEmpty() == false) {
					GpxCreator.createGpx(gpxName + "_" + i,
							Arrays.asList(c, nc), alternatives);
				} else
					GpxCreator.createGpx(gpxName + "_" + i,
							Arrays.asList(c, nc));
			}
			if (neighbours.isEmpty())
				GpxCreator.createGpx(gpxName + "_empty", Arrays.asList(c, c),
						alternatives);
		}

	}
	
	
	private void writeOSM(String name, List<ConvertedWay> convertedWays){
		//TODO: comment or remove
		/*
		if (gpxPath == null)
			return;
		File outDir = new File(gpxPath + "/.");
		if (outDir.getParentFile() != null) {
			outDir.getParentFile().mkdirs();
		} 		
		Map<String,byte[]> dummyMap = new HashMap<>();
		for (int pass = 1; pass <= 2; pass ++){
			IdentityHashMap<Coord, Integer> allPoints = new IdentityHashMap<>();
			uk.me.parabola.splitter.Area bounds = new uk.me.parabola.splitter.Area(
					bbox.getMinLat(),bbox.getMinLong(),bbox.getMaxLat(),bbox.getMaxLong());

			
			O5mMapWriter writer = new O5mMapWriter(bounds, outDir, 0, 0, dummyMap, dummyMap);
			writer.initForWrite();
			Integer nodeId;
			try {

				for (ConvertedWay cw: convertedWays){
					if (cw == null)
						continue;
					for (Coord p: cw.getPoints()){
						nodeId = allPoints.get(p);
						if (nodeId == null){
							nodeId = allPoints.size();
							allPoints.put(p, nodeId);
							uk.me.parabola.splitter.Node nodeOut = new  uk.me.parabola.splitter.Node();				
							if (pass == 1)
								nodeOut.set(nodeId+1000000000L, p.getLatDegrees(), p.getLonDegrees()); // high prec
							else 
								nodeOut.set(nodeId+1000000000L, Utils.toDegrees(p.getLatitude()), Utils.toDegrees(p.getLongitude()));
							if (p instanceof CoordPOI){
								for (Map.Entry<String, String> tagEntry : ((CoordPOI) p).getNode().getTagEntryIterator()) {
									nodeOut.addTag(tagEntry.getKey(), tagEntry.getValue());
								}
							}
							writer.write(nodeOut);
						}
					}
				}
				for (int w = 0; w < convertedWays.size(); w++){
					ConvertedWay cw = convertedWays.get(w);
					if (cw == null)
						continue;
					Way way = cw.getWay();
					uk.me.parabola.splitter.Way wayOut = new uk.me.parabola.splitter.Way();
					for (Coord p: way.getPoints()){
						nodeId = allPoints.get(p);
						assert nodeId != null;
						wayOut.addRef(nodeId+1000000000L);
					}
					for (Map.Entry<String, String> tagEntry : way.getTagEntryIterator()) {
						wayOut.addTag(tagEntry.getKey(), tagEntry.getValue());
					}
					
					wayOut.setId(way.getId());
					
					writer.write(wayOut);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			writer.finishWrite();
			File f = new File(outDir.getAbsoluteFile() , "00000000.o5m");
			File ren = new File(outDir.getAbsoluteFile() , name+((pass==1) ? "_hp":"_mu") + ".o5m");
			if (ren.exists())
				ren.delete();
			f.renameTo(ren);
		}
		*/
	}
	
	 
	private static double calcBearingError(Coord p1, Coord p2){
		if (p1.equals(p2) || p1.highPrecEquals(p2)) {
			return Double.MAX_VALUE;
		}
		double realBearing = p1.bearingTo(p2);
		double displayedBearing = p1.getDisplayedCoord().bearingTo(p2.getDisplayedCoord());
		double err = displayedBearing - realBearing;
		while(err > 180)
			err -= 360;
		while(err < -180)
			err += 360;
		return Math.abs(err);
	}

	/**
	 * calculate distance of point in the middle to line c1,c2 using herons formula
	 * @param cm point in the middle
	 * @param c1 
	 * @param c2
	 * @return distance in meter
	 */
	private static double distToLineHeron(Coord cm, Coord c1, Coord c2){
		double ab = c1.distance(c2);
		double ap = cm.distance(c1);
		double bp = cm.distance(c2);
		double abpa = (ab+ap+bp)/2;
		double distance = 2 * Math.sqrt(abpa * (abpa-ab) * (abpa-ap) * (abpa-bp)) / ab;
		return distance;
		
	}

	/**
	 * Calculate the rounding error tolerance for a given point.
	 * The latitude error may be max higher. Maybe this should be 
	 * @param p0
	 * @return
	 */
	private static double calcMaxErrorDistance(Coord p0){
		Coord test = new Coord(p0.getLatitude(),p0.getLongitude()+1);
		double lonErr = p0.getDisplayedCoord().distance(test) / 2;
		return lonErr;
	}

	/**
	 * Remove obsolete points on straight lines and spikes
	 * and some wrong angles caused by rounding errors.  
	 * TODO: optimise by moving 
	 * @param points list of coordinates that form a shape
	 * @return reduced list 
	 */
	public static List<Coord> fixAnglesInShape(List<Coord> points) {
		List<Coord> modifiedPoints = new ArrayList<>(points.size());
		double maxErrorDistance = calcMaxErrorDistance(points.get(0));
		
		int n = points.size();
		// scan through the way's points looking for points which are
		// on almost straight line and therefore obsolete
		for (int i = 0; i+1 < points.size(); i++) {
			Coord c1;
			if (modifiedPoints.size() > 0)
				c1 = modifiedPoints.get(modifiedPoints.size()-1);
			else {
				c1 = (i > 0) ? points.get(i-1):points.get(n-2);
			}
			Coord cm = points.get(i);
			if (cm.highPrecEquals(c1)){
				if (modifiedPoints.size() > 1){
					modifiedPoints.remove(modifiedPoints.size()-1);
					c1 = modifiedPoints.get(modifiedPoints.size()-1); // might be part of spike
				} else {
					continue;
				}
			}
			Coord c2 = points.get(i+1);
			int straightTest = Utils.isHighPrecStraight(c1, cm, c2);
			if (straightTest == Utils.STRICTLY_STRAIGHT || straightTest == Utils.STRAIGHT_SPIKE){
				continue;
			}
			double realAngle = Utils.getAngle(c1, cm, c2);
			if (Math.abs(realAngle) < MAX_DIFF_ANGLE_STRAIGHT_LINE){ 
				double distance = distToLineHeron(cm, c1, c2);
				if (distance < maxErrorDistance)
					continue;
			}
			modifiedPoints.add(cm);
		}
		if (modifiedPoints.get(0) != modifiedPoints.get(modifiedPoints.size()-1))
			modifiedPoints.add(modifiedPoints.get(0));
		return modifiedPoints;
	}
}
