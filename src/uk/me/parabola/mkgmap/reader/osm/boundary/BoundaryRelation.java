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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import uk.me.parabola.imgfmt.app.Area;
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
			boundary = new Boundary(outerResultArea, this.getEntryIteratable());
			
			boundary.getTags().put("mkgmap:boundaryid", "r"+this.getId());
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
		log.threadTag("r"+getId());
	
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
		closeWays(polygons);
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
				outerResultArea.add(Java2DConverter.createArea(currentPolygon.polygon.getPoints()));

				for (Way outerWay : currentPolygon.polygon.getOriginalWays()) {
					if (outmostPolygonProcessing) {
						for (Entry<String, String> tag : outerWay.getEntryIteratable()) {
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
		
		
		if (hasTags(this)) {
			outerTags.clear();
			for (Entry<String,String> mpTags : getEntryIteratable()) {
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
		
		if (getTag("name") == null)
			outerResultArea = null;
		
	}

	private void removeOutOfBbox(List<JoinedWay> polygons) {
		ListIterator<JoinedWay> pIter = polygons.listIterator();
		while (pIter.hasNext()) {
			JoinedWay w = pIter.next();
			if (w.isClosed() == false) {
				// the way is not closed
				// check if one of start/endpoint is out of the bounding box
				// in this case it is too risky to close it
				if (getBbox().contains(w.getPoints().get(0)) == false
						|| getBbox().contains(
								w.getPoints().get(w.getPoints().size() - 1)) == false) {
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
