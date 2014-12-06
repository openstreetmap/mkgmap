/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.MultiPolygonRelation;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;


public class BoundaryRelation extends MultiPolygonRelation {
	private static final Logger log = Logger
	.getLogger(BoundaryRelation.class);

	private java.awt.geom.Area outerResultArea;
	
	/** keeps the result of the multipolygon processing */
	private Boundary boundary;
	
	public BoundaryRelation(Relation other, Map<Long, Way> wayMap, Area bbox) {
		super(other, wayMap, bbox);
	}
	
	public Boundary getBoundary() {
		if (boundary == null) {
			if (outerResultArea == null) {
				return null;
			}
			boundary = new Boundary(outerResultArea, this.getTagEntryIterator(),"r"+this.getId());
			outerResultArea = null;
		}
		return boundary;
	}
	
	/**
	 * Process the ways in this relation. Joins way with the role "outer" Adds
	 * ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		log.info("Processing multipolygon", toBrowseURL());
	
		List<Way> allWays = getSourceWays();
		
//		// check if the multipolygon itself or the non inner member ways have a tag
//		// if not it does not make sense to process it and we could save the time
//		boolean shouldProcess = hasStyleRelevantTags(this);
//		if (shouldProcess == false) {
//			for (Way w : allWays) {
//				shouldProcess = hasStyleRelevantTags(w);
//				if (shouldProcess) {
//					break;
//				}
//			}
//		}
//		if (shouldProcess==false) {
//			log.info("Do not process multipolygon",getId(),"because it has no style relevant tags.");
//			return;
//		}

		
		// create an Area for the bbox to clip the polygons
		bboxArea = Java2DConverter.createBoundsArea(getBbox()); 

		// join all single ways to polygons, try to close ways and remove non closed ways 
		polygons = joinWays(allWays);
		
		outerWaysForLineTagging = new HashSet<Way>();
		outerTags = new HashMap<String,String>();
		
		removeOutOfBbox(polygons);

		boolean changed = true;
		while (changed) {
			changed = false;
			while (connectUnclosedWays(polygons)) {
				changed = true;
			}		
			closeWays(polygons);
		}
		
		removeUnclosedWays(polygons);
		
		// now only closed ways are left => polygons only

		// check if we have at least one polygon left
		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " does not contain a closed polygon.");
			tagOuterWays();
			cleanup();
			return;
		}

		removeWaysOutsideBbox(polygons);

		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " is completely outside the bounding box. It is not processed.");
			tagOuterWays();
			cleanup();
			return;
		}
		
		// the intersectingPolygons marks all intersecting/overlapping polygons
		intersectingPolygons = new HashSet<JoinedWay>();
		
		// check which polygons lie inside which other polygon 
		createContainsMatrix(polygons);

		// unfinishedPolygons marks which polygons are not yet processed
		unfinishedPolygons = new BitSet(polygons.size());
		unfinishedPolygons.set(0, polygons.size());

		// create bitsets which polygons belong to the outer and to the inner role
		innerPolygons = new BitSet();
		taggedInnerPolygons = new BitSet();
		outerPolygons = new BitSet();
		taggedOuterPolygons = new BitSet();
		
		int wi = 0;
		for (Way w : polygons) {
			String role = getRole(w);
			if ("inner".equals(role)) {
				innerPolygons.set(wi);
				taggedInnerPolygons.set(wi);
			} else if ("outer".equals(role)) {
				outerPolygons.set(wi);
				taggedOuterPolygons.set(wi);
			} else {
				// unknown role => it could be both
				innerPolygons.set(wi);
				outerPolygons.set(wi);
			}
			wi++;
		}

		if (outerPolygons.isEmpty()) {
			log.warn("Multipolygon", toBrowseURL(),
				"does not contain any way tagged with role=outer or empty role.");
			cleanup();
			return;
		}

		Queue<PolygonStatus> polygonWorkingQueue = new LinkedBlockingQueue<PolygonStatus>();
		BitSet nestedOuterPolygons = new BitSet();
		BitSet nestedInnerPolygons = new BitSet();

		BitSet outmostPolygons ;
		BitSet outmostInnerPolygons = new BitSet();
		boolean outmostInnerFound;
		do {
			outmostInnerFound = false;
			outmostPolygons = findOutmostPolygons(unfinishedPolygons);

			if (outmostPolygons.intersects(taggedInnerPolygons)) {
				outmostInnerPolygons.or(outmostPolygons);
				outmostInnerPolygons.and(taggedInnerPolygons);

				if (log.isDebugEnabled())
					log.debug("wrong inner polygons: " + outmostInnerPolygons);
				// do not process polygons tagged with role=inner but which are
				// not contained by any other polygon
				unfinishedPolygons.andNot(outmostInnerPolygons);
				outmostPolygons.andNot(outmostInnerPolygons);
				outmostInnerFound = true;
			}
		} while (outmostInnerFound);
		
		if (!outmostPolygons.isEmpty()) {
			polygonWorkingQueue.addAll(getPolygonStatus(outmostPolygons, "outer"));
		}

		boolean outmostPolygonProcessing = true;
		
		
		outerResultArea = new java.awt.geom.Area();
		
		while (!polygonWorkingQueue.isEmpty()) {

			// the polygon is not contained by any other unfinished polygon
			PolygonStatus currentPolygon = polygonWorkingQueue.poll();

			// this polygon is now processed and should not be used by any
			// further step
			unfinishedPolygons.clear(currentPolygon.index);

			BitSet polygonContains = new BitSet();
			polygonContains.or(containsMatrix.get(currentPolygon.index));
			// use only polygon that are contained by the polygon
			polygonContains.and(unfinishedPolygons);
			// polygonContains is the intersection of the unfinished and
			// the contained polygons

			// get the holes
			// these are all polygons that are in the main polygon
			// and that are not contained by any other polygon
			boolean holesOk;
			BitSet holeIndexes;
			do {
				holeIndexes = findOutmostPolygons(polygonContains);
				holesOk = true;

				if (currentPolygon.outer) {
					// for role=outer only role=inner is allowed
					if (holeIndexes.intersects(taggedOuterPolygons)) {
						BitSet addOuterNestedPolygons = new BitSet();
						addOuterNestedPolygons.or(holeIndexes);
						addOuterNestedPolygons.and(taggedOuterPolygons);
						nestedOuterPolygons.or(addOuterNestedPolygons);
						holeIndexes.andNot(addOuterNestedPolygons);
						// do not process them
						unfinishedPolygons.andNot(addOuterNestedPolygons);
						polygonContains.andNot(addOuterNestedPolygons);
						
						// recalculate the holes again to get all inner polygons 
						// in the nested outer polygons
						holesOk = false;
					}
				} else {
					// for role=inner both role=inner and role=outer is supported
					// although inner in inner is not officially allowed
					if (holeIndexes.intersects(taggedInnerPolygons)) {
						// process inner in inner but issue a warning later
						BitSet addInnerNestedPolygons = new BitSet();
						addInnerNestedPolygons.or(holeIndexes);
						addInnerNestedPolygons.and(taggedInnerPolygons);
						nestedInnerPolygons.or(addInnerNestedPolygons);
					}
				}
			} while (!holesOk);

			ArrayList<PolygonStatus> holes = getPolygonStatus(holeIndexes, 
				(currentPolygon.outer ? "inner" : "outer"));

			// these polygons must all be checked for holes
			polygonWorkingQueue.addAll(holes);

			if (currentPolygon.outer) {
				// add the original ways to the list of ways that get the line tags of the mp
				// the joined ways may be changed by the auto closing algorithm
				outerWaysForLineTagging.addAll(currentPolygon.polygon.getOriginalWays());
			}
			
			if (currentPolygon.outer) {
				java.awt.geom.Area toAdd = Java2DConverter.createArea(currentPolygon.polygon.getPoints());
				if (outerResultArea.isEmpty())
					outerResultArea = toAdd;
				else
					outerResultArea.add(toAdd);

				for (Way outerWay : currentPolygon.polygon.getOriginalWays()) {
					if (outmostPolygonProcessing) {
						for (Entry<String, String> tag : outerWay.getTagEntryIterator()) {
							outerTags.put(tag.getKey(), tag.getValue());
						}
						outmostPolygonProcessing = false;
					} else {
						for (String tag : new ArrayList<String>(outerTags.keySet())) {
							if (outerTags.get(tag).equals(outerWay.getTag(tag)) == false) {
								outerTags.remove(tag);
							}
						}
					}
				}
			} else {
				outerResultArea.subtract(Java2DConverter
						.createArea(currentPolygon.polygon.getPoints()));
			}
		}
		
		// TODO tagging of the outer ways
		
//		if (log.isLoggable(Level.WARNING) && 
//				(outmostInnerPolygons.cardinality()+unfinishedPolygons.cardinality()+nestedOuterPolygons.cardinality()+nestedInnerPolygons.cardinality() >= 1)) {
//			log.warn("Multipolygon", toBrowseURL(), "contains errors.");
//
//			BitSet outerUnusedPolys = new BitSet();
//			outerUnusedPolys.or(unfinishedPolygons);
//			outerUnusedPolys.or(outmostInnerPolygons);
//			outerUnusedPolys.or(nestedOuterPolygons);
//			outerUnusedPolys.or(nestedInnerPolygons);
//			outerUnusedPolys.or(unfinishedPolygons);
//			// use only the outer polygons
//			outerUnusedPolys.and(outerPolygons);
//			for (JoinedWay w : getWaysFromPolygonList(outerUnusedPolys)) {
//				outerWaysForLineTagging.addAll(w.getOriginalWays());
//			}
//			
//			runIntersectionCheck(unfinishedPolygons);
//			runOutmostInnerPolygonCheck(outmostInnerPolygons);
//			runNestedOuterPolygonCheck(nestedOuterPolygons);
//			runNestedInnerPolygonCheck(nestedInnerPolygons);
//			runWrongInnerPolygonCheck(unfinishedPolygons, innerPolygons);
//
//			// we have at least one polygon that could not be processed
//			// Probably we have intersecting or overlapping polygons
//			// one possible reason is if the relation overlaps the tile
//			// bounds
//			// => issue a warning
//			List<JoinedWay> lostWays = getWaysFromPolygonList(unfinishedPolygons);
//			for (JoinedWay w : lostWays) {
//				log.warn("Polygon", w, "is not processed due to an unknown reason.");
//				logWayURLs(Level.WARNING, "-", w);
//			}
//		}
//
		
		
		if (hasStyleRelevantTags(this)) {
			outerTags.clear();
			for (Entry<String,String> mpTags : getTagEntryIterator()) {
				if ("type".equals(mpTags.getKey())==false) {
					outerTags.put(mpTags.getKey(), mpTags.getValue());
				}
			}
		} else {
			for (Entry<String,String> mpTags : outerTags.entrySet()) {
				addTag(mpTags.getKey(), mpTags.getValue());
			}
		}
		
		// Go through all original outer ways, create a copy, tag them
		// with the mp tags and mark them only to be used for polyline processing
		// This enables the style file to decide if the polygon information or
		// the simple line information should be used.
		for (Way orgOuterWay : outerWaysForLineTagging) {
//			Way lineTagWay =  new Way(FakeIdGenerator.makeFakeId(), orgOuterWay.getPoints());
//			lineTagWay.setName(orgOuterWay.getName());
//			lineTagWay.addTag(STYLE_FILTER_TAG, STYLE_FILTER_LINE);
			for (Entry<String,String> tag : outerTags.entrySet()) {
//				lineTagWay.addTag(tag.getKey(), tag.getValue());
				
				// remove the tag from the original way if it has the same value
				if (tag.getValue().equals(orgOuterWay.getTag(tag.getKey()))) {
					removeTagsInOrgWays(orgOuterWay, tag.getKey());
				}
			}
			
//			if (log.isDebugEnabled())
//				log.debug("Add line way", lineTagWay.getId(), lineTagWay.toTagString());
//			tileWayMap.put(lineTagWay.getId(), lineTagWay);
		}
		
		postProcessing();
		cleanup();
	}
	protected boolean connectUnclosedWays(List<JoinedWay> allWays) {
		List<JoinedWay> unclosed = new ArrayList<JoinedWay>();

		for (JoinedWay w : allWays) {
			if (w.hasIdenticalEndPoints() == false) {
				unclosed.add(w);
			}
		}
		// try to connect ways lying outside or on the bbox
		if (unclosed.size() >= 2) {
			log.debug("Checking",unclosed.size(),"unclosed ways for connections outside the bbox");
			Map<Coord, JoinedWay> outOfBboxPoints = new IdentityHashMap<Coord, JoinedWay>();
			
			// check all ways for endpoints outside or on the bbox
			for (JoinedWay w : unclosed) {
				Coord c1 = w.getPoints().get(0);
//				if (bbox.insideBoundary(c1)==false) {
//					log.debug("Point",c1,"of way",w.getId(),"outside bbox");
					outOfBboxPoints.put(c1, w);
//				}

				Coord c2 = w.getPoints().get(w.getPoints().size()-1);
//				if (bbox.insideBoundary(c2)==false) {
//					log.debug("Point",c2,"of way",w.getId(),"outside bbox");
					outOfBboxPoints.put(c2, w);
//				}
			}
			
			if (outOfBboxPoints.size() < 2) {
				log.debug(outOfBboxPoints.size(),"point outside the bbox. No connection possible.");
				return false;
			}
			
			List<ConnectionData> coordPairs = new ArrayList<ConnectionData>();
			ArrayList<Coord> coords = new ArrayList<Coord>(outOfBboxPoints.keySet());
			for (int i = 0; i < coords.size(); i++) {
				for (int j = i + 1; j < coords.size(); j++) {
					ConnectionData cd = new ConnectionData();
					cd.c1 = coords.get(i);
					cd.c2 = coords.get(j);
					cd.w1 = outOfBboxPoints.get(cd.c1);					
					cd.w2 = outOfBboxPoints.get(cd.c2);					
					
//					if (lineCutsBbox(cd.c1, cd.c2 )) {
//						// Check if the way can be closed with one additional point
//						// outside the bounding box.
//						// The additional point is combination of the coords of both endpoints.
//						// It works if the lines from the endpoints to the additional point does
//						// not cut the bounding box.
//						// This can be removed when the splitter guarantees to provide logical complete
//						// multi-polygons.
//						Coord edgePoint1 = new Coord(cd.c1.getLatitude(), cd.c2
//								.getLongitude());
//						Coord edgePoint2 = new Coord(cd.c2.getLatitude(), cd.c1
//								.getLongitude());
//
//						if (lineCutsBbox(cd.c1, edgePoint1) == false
//								&& lineCutsBbox(edgePoint1, cd.c2) == false) {
//							cd.imC = edgePoint1;
//						} else if (lineCutsBbox(cd.c1, edgePoint2) == false
//								&& lineCutsBbox(edgePoint2, cd.c2) == false) {
//							cd.imC = edgePoint1;
//						} else {
//							// both endpoints are on opposite sides of the bounding box
//							// automatically closing such points would create wrong polygons in most cases
//							continue;
//						}
//						cd.distance = cd.c1.distance(cd.imC) + cd.imC.distance(cd.c2);
//					} else {
						cd.distance = cd.c1.distance(cd.c2);
//					}
					coordPairs.add(cd);
				}
			}
			
			if (coordPairs.isEmpty()) {
				log.debug("All potential connections cross the bbox. No connection possible.");
				return false;
			} else {
				// retrieve the connection with the minimum distance
				ConnectionData minCon = Collections.min(coordPairs,
						new Comparator<ConnectionData>() {
							public int compare(ConnectionData o1,
									ConnectionData o2) {
								return Double.compare(o1.distance, o2.distance);
							}
						});
				
				if (minCon.distance < getMaxCloseDist()) {

					if (minCon.w1 == minCon.w2) {
						log.debug("Close a gap in way", minCon.w1);
						if (minCon.imC != null)
							minCon.w1.getPoints().add(minCon.imC);
						minCon.w1.closeWayArtificially();
					} else {
						log.debug("Connect", minCon.w1, "with", minCon.w2);
						if (minCon.w1.getPoints().get(0) == minCon.c1) {
							Collections.reverse(minCon.w1.getPoints());
						}
						if (minCon.w2.getPoints().get(0) != minCon.c2) {
							Collections.reverse(minCon.w2.getPoints());
						}

						minCon.w1.getPoints().addAll(minCon.w2.getPoints());
						minCon.w1.addWay(minCon.w2);
						allWays.remove(minCon.w2);
					}
					return true;
				}
			}
		}
		return false;
	}
	
	private double getMaxCloseDist() {
		double dist = 1000;
		String admString= getTag("admin_level");
		
		if ("2".equals(admString)) {
			dist = 50000;
		} else if ("3".equals(admString)) {
			dist = 20000;
		}else if ("4".equals(admString)) {
			dist = 4000;
		}
		return dist;
	}
	
	protected void closeWays(ArrayList<JoinedWay> wayList) {
		for (JoinedWay way : wayList) {
			if (way.hasIdenticalEndPoints() || way.getPoints().size() < 3) {
				continue;
			}
			Coord p1 = way.getPoints().get(0);
			Coord p2 = way.getPoints().get(way.getPoints().size() - 1);

			if (getBbox().insideBoundary(p1) == false
					&& getBbox().insideBoundary(p2) == false) {
				// both points lie outside the bbox or on the bbox

				// check if both points are on the same side of the bounding box
				if ((p1.getLatitude() <= getBbox().getMinLat() && p2.getLatitude() <= getBbox()
						.getMinLat())
						|| (p1.getLatitude() >= getBbox().getMaxLat() && p2
								.getLatitude() >= getBbox().getMaxLat())
						|| (p1.getLongitude() <= getBbox().getMinLong() && p2
								.getLongitude() <= getBbox().getMinLong())
						|| (p1.getLongitude() >= getBbox().getMaxLong() && p2
								.getLongitude() >= getBbox().getMaxLong())) {
					// they are on the same side outside of the bbox
					// so just close them without worrying about if
					// they intersect itself because the intersection also
					// is outside the bbox
					way.closeWayArtificially();
					log.info("Endpoints of way", way,
							"are both outside the bbox. Closing it directly.");
					continue;
				}
			}
			
			Line2D closingLine = new Line2D.Float(p1.getLongitude(), p1
					.getLatitude(), p2.getLongitude(), p2.getLatitude());

			boolean intersects = false;
			Coord lastPoint = null;
			// don't use the first and the last point
			// the closing line can intersect only in one point or complete.
			// Both isn't interesting for this check
			for (Coord thisPoint : way.getPoints().subList(1,
					way.getPoints().size() - 1)) {
				if (lastPoint != null) {
					if (closingLine.intersectsLine(lastPoint.getLongitude(),
							lastPoint.getLatitude(), thisPoint.getLongitude(),
							thisPoint.getLatitude())) {
						intersects = true;
						break;
					}
				}
				lastPoint = thisPoint;
			}

			if (!intersects) {
				// close the polygon
				// the new way segment does not intersect the rest of the
				// polygon
				
				// calc the distance to close
				double closeDist = way.getPoints().get(0).distance(way.getPoints().get(way.getPoints().size()-1));
				
				if (closeDist <= getMaxCloseDist()) {
					log.info("Closing way", way);
					log.info("from", way.getPoints().get(0).toOSMURL());
					log.info("to", way.getPoints().get(way.getPoints().size() - 1)
							.toOSMURL());
					// mark this ways as artificially closed
					way.closeWayArtificially();
				}
			}
		}
	}

	private void removeOutOfBbox(List<JoinedWay> polygons) {
		ListIterator<JoinedWay> pIter = polygons.listIterator();
		while (pIter.hasNext()) {
			JoinedWay w = pIter.next();
			Coord first = w.getPoints().get(0);
			Coord last =  w.getPoints().get(w.getPoints().size() - 1);
			if (first != last) {
				// the way is not closed
				// check if one of start/endpoint is out of the bounding box
				// in this case it is too risky to close it
				if (getBbox().contains(first) == false || getBbox().contains(last) == false) {
					pIter.remove();
				}
			}
		}

	}

	protected void cleanup() {
		super.cleanup();
		this.getElements().clear();
		((ArrayList<?>)this.getElements()).trimToSize();
	}
}
