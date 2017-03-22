/*
 * Copyright (C) 2011 - 2012.
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

package uk.me.parabola.mkgmap.reader.osm;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryGrid;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryQuadTree;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryUtil;
import uk.me.parabola.util.EnhancedProperties;

public class LocationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(LocationHook.class);
	// the resulting assignments are logged with this extra logger
	// so that it is possible to log only the results of the location hook
	private static final Logger resultLog = Logger.getLogger(LocationHook.class.getName()+".results");
	
	// counters for stats
	private long cntQTSearch = 0;
	private long cntNotFnd = 0;
	private long cntwayNotFnd = 0;
	
	private BoundaryGrid boundaryGrid;

	private ElementSaver saver;
	
	private String boundaryDirName;

	
	/** this static object is used to synchronize the check if the bounds directory contains any bounds */
	private static final Object BOUNDS_CHECK_LOCK = new Object();
	
	/** Stores the name of the bounds dir/file that has been checked. Static so that multiple threads can access. */
	private static String checkedBoundaryDirName;
	/** stores the result of the bounds dir/file check */
	private static boolean checkBoundaryDirOk;
	
	private EnhancedProperties props;

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		boundaryDirName = props.getProperty("bounds");
		
		if (boundaryDirName == null) {
			// bounds property not set
			return false;
		}
		
		this.props = props;
		this.saver = saver;

		long t1 = System.currentTimeMillis();

		synchronized (BOUNDS_CHECK_LOCK) {
			// checking of the boundary dir is expensive
			// check once only and reuse the result
			if (boundaryDirName.equals(checkedBoundaryDirName)) {
				if (checkBoundaryDirOk == false) {
					log.error("Disable LocationHook because bounds directory is unusable. Dir: "+boundaryDirName);
					return false;
				}
			} else {
				checkedBoundaryDirName = boundaryDirName;
				checkBoundaryDirOk = false;

				List<String> boundaryFiles = BoundaryUtil.getBoundaryDirContent(boundaryDirName);
				if (boundaryFiles == null || boundaryFiles.size() == 0) {
					log.error("LocationHook is disabled because no bounds files are available. Dir: "
							+ boundaryDirName);
					return false;
				}
					// passed all checks => boundaries are okay
				checkBoundaryDirOk = true;
			}
		}
		log.info("Checking bounds dir took", (System.currentTimeMillis() - t1), "ms");
		return true;
	}

	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Starting with location hook");
		
		Area bbox = saver.getBoundingBox();
		Area nodesBounds = saver.getDataBoundingBox();
		// calculate the needed bounding box
		Area searchBounds = bbox.intersect(nodesBounds);
		boundaryGrid = new BoundaryGrid(boundaryDirName, searchBounds, props);
		processLocationRelevantElements();

		boundaryGrid = null;
		
		long dt = (System.currentTimeMillis() - t1);
		log.info("======= LocationHook Stats =====");             
		log.info("QuadTree searches    :", cntQTSearch);             
		log.info("unsuccesfull         :", cntNotFnd);             
		log.info("unsuccesfull for ways:", cntwayNotFnd);             
		log.info("Location hook finished in", dt, "ms");

	}

	/**
	 * Iterate over all elements for which the boundary assignment should be performed.
	 */
	private void processLocationRelevantElements() {
		// process all nodes that might be converted to a garmin node (tagcount > 0)
		for (Node node : saver.getNodes().values()) {
			if (node.getTagCount() > 0) {
				if (saver.getBoundingBox().contains(node.getLocation())){
					processElem(node);
					if (resultLog.isDebugEnabled())
						resultLog.debug("N", node.getId(), locationTagsToString(node));
				}
			}
		}

		// process  all ways that might be converted to a garmin way (tagcount > 0)
		for (Way way : saver.getWays().values()) {
			if (way.getTagCount() > 0) {
				processElem(way);
				if (resultLog.isDebugEnabled())
					resultLog.debug("W", way.getId(), locationTagsToString(way));
			}
		}
		
		// process all multipolygons - the add-pois-to-area function uses its
		// center point and its tags so the mp must be tagged itself with the bounds
		// tags
		for (Relation r : saver.getRelations().values()) {
			if (r instanceof MultiPolygonRelation) {
				// check if the mp could be processed
				Coord mpCenter = ((MultiPolygonRelation) r).getCofG();
				if (mpCenter != null && saver.getBoundingBox().contains(mpCenter)){
					// create a fake node for which the bounds information is collected
					Node mpNode = new Node(r.getOriginalId(), mpCenter);
					mpNode.setFakeId();
					processElem(mpNode);
					// copy the bounds tags back to the multipolygon
					for (String boundsTag : BoundaryQuadTree.mkgmapTagsArray) {
						String tagValue = mpNode.getTag(boundsTag);
						if (tagValue != null) {
							r.addTag(boundsTag, tagValue);
						}
					}
					if (resultLog.isDebugEnabled())
						resultLog.debug("R", r.getId(), locationTagsToString(r));
				}
			}
		}
	}

	/**
	 * Extract the location info and perform a test 
	 * against the BoundaryGrid. If found, assign the tags.  
	 * @param elem A way or Node
	 */
	private void processElem(Element elem){
		Tags tags = null;

		if (elem instanceof Node){
			Node node = (Node) elem;
			tags = search(node.getLocation());
		}
		else if (elem instanceof Way){
			Way way = (Way) elem;
			// try the mid point of the way first
			int middle = way.getPoints().size() / 2;
			tags = search(way.getPoints().get(middle));
			if (tags == null){
				// try 1st point next
				tags = search(way.getPoints().get(0));
			}
			if (tags == null){
				// try last point next
				tags = search(way.getPoints().get(way.getPoints().size()-1));
			}
			if (tags == null){
				// still not found, try rest
				for (int i = 1; i < way.getPoints().size()-1; i++){
					if (i == middle)
						continue;
					tags = search(way.getPoints().get(i));
					if (tags != null) 
						break;
				}
			}
			if (tags == null)
				++cntwayNotFnd;
		}

		if (tags == null){
			++cntNotFnd;
		}
		else{
			// tag the element with all tags referenced by the boundary
			Iterator<Entry<Short,String>> tagIter = tags.entryShortIterator();
			while (tagIter.hasNext()) {
				Entry<Short,String> tag = tagIter.next();
				if (elem.getTag(tag.getKey()) == null){
					elem.addTag(tag.getKey(),tag.getValue());
				}
			}
		}
	}
	
	/**
	 * perform search in grid and maintain statistic counter
	 * @param co a point that is to be searched
	 * @return location relevant tags or null
	 */
	private Tags search(Coord co){
		if (saver.getBoundingBox().contains(co)){
			++cntQTSearch;
			return boundaryGrid.get(co);
		}
		else 
			return null;
	}
			
	/**
	 * Debugging:
	 * Create a string with location relevant tags ordered by admin_level.
	 * Can be used to compare results with tools like diff.
	 * @param elem the element 
	 * @return A new String object
	 */
	private String locationTagsToString(Element elem){
		StringBuilder res = new StringBuilder();
		for (int i = BoundaryQuadTree.mkgmapTagsArray.length-1; i >= 0; --i){
			String tagVal = elem.getTag(BoundaryQuadTree.mkgmapTagsArray[i] );
			if (tagVal != null)
				res.append(tagVal);
			res.append(";");
		}
		return res.toString();
	}

}


