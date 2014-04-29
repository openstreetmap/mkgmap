/*
 * Copyright (C) 2011.
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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Adds a POI for each area and multipolygon with the same tags in case the add-pois-to-areas option
 * is set. Adds multiple POIs to each line if the add-pois-to-lines option is set.<br/>
 * <br/>
 * <code>add-pois-to-areas</code><br/>
 * Artificial areas created by 
 * multipolygon relation processing are not used for POI creation. The location of the POI 
 * is determined in different ways.<br/>
 * Element is of type {@link Way}:
 * <ul>
 * <li>the first node tagged with building=entrance</li>
 * <li>the center point of the area</li>
 * </ul>
 * Element is of type {@link MultiPolygonRelation}:
 * <ul>
 * <li>the node with role=label</li>
 * <li>the center point of the biggest area</li>
 * </ul>
 * Each node created is tagged with mkgmap:area2poi=true.<br/>
 * <br/>
 * <code>add-pois-to-lines</code><br/>
 * Adds POIs to lines. Each POI is tagged with mkgmap:line2poi=true.<br/>
 * The following POIs are created for each line:
 * <ul>
 * <li>mkgmap:line2poitype=start: The first point of the line</li>
 * <li>mkgmap:line2poitype=end: The last point of the line</li>
 * <li>mkgmap:line2poitype=inner: Each inner point of the line</li>
 * <li>mkgmap:line2poitype=mid: POI at the middle distance of the line</li>
 * </ul>
 * @author WanMil
 */
public class POIGeneratorHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(POIGeneratorHook.class);

	private List<Entry<String,String>> poiPlacementTags; 
	
	private ElementSaver saver;
	
	private boolean poisToAreas = false;
	private boolean poisToLines = false;
	
	/** Name of the bool tag that is set to true if a POI is created from an area */
	public static final short AREA2POI_TAG = TagDict.getInstance().xlate("mkgmap:area2poi");
	public static final short LINE2POI_TAG = TagDict.getInstance().xlate("mkgmap:line2poi");
	public static final short LINE2POI_TYPE_TAG  = TagDict.getInstance().xlate("mkgmap:line2poitype");
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		poisToAreas = props.containsKey("add-pois-to-areas");
		poisToLines = props.containsKey("add-pois-to-lines");
		
		if ((poisToAreas || poisToLines) == false) {
			log.info("Disable Areas2POIHook because add-pois-to-areas and add-pois-to-lines option is not set.");
			return false;
		}
		
		this.poiPlacementTags = getPoiPlacementTags(props);
		
		this.saver = saver;
		
		return true;
	}
	
	/**
	 * Reads the tag definitions of the option poi2area-placement-tags from the given properties.
	 * @param props mkgmap options
	 * @return the parsed tag definition list
	 */
	public static List<Entry<String,String>> getPoiPlacementTags(EnhancedProperties props) {
		if (props.containsKey("add-pois-to-areas") == false) {
			return Collections.emptyList();
		}
		
		List<Entry<String,String>> tagList = new ArrayList<Entry<String,String>>();
		
		String placementDefs = props.getProperty("pois-to-areas-placement", "entrance=main;entrance=yes;building=entrance");
		placementDefs = placementDefs.trim();
		
		if (placementDefs.length() == 0) {
			// the POIs should be placed in the center only
			// => return an empty list
			return tagList;
		}
		
		String[] placementDefsParts = placementDefs.split(";");
		for (String placementDef : placementDefsParts) {
			int ind = placementDef.indexOf('=');
			String tagName = null;
			String tagValue = null;
			if (ind < 0) {
				// only the tag is defined => interpret it as tag=*
				tagName = placementDef;
				tagValue = null;
			} else if (ind > 0) {
				tagName = placementDef.substring(0,ind);
				tagValue = placementDef.substring(ind+1);
			} else {
				log.error("Option pois-to-areas-placement contains a tag that starts with '='. This is not allowed. Ignoring it.");
				continue;
			}
			tagName = tagName.trim();
			if (tagName.length() == 0) {
				log.error("Option pois-to-areas-placement contains a whitespace tag  '='. This is not allowed. Ignoring it.");
				continue;
			}
			if (tagValue != null) {
				tagValue = tagValue.trim();
				if (tagValue.length() == 0 || "*".equals(tagValue)) {
					tagValue = null;
				} 
			}
			Entry<String,String> tag = new AbstractMap.SimpleImmutableEntry<String,String>(tagName, tagValue);
			tagList.add(tag);
		}
		return tagList;
	}
	

	public Set<String> getUsedTags() {
		// return all tags defined in the poiPlacementTags
		Set<String> tags = new HashSet<String>();
		for (Entry<String,String> poiTag : poiPlacementTags) {
			tags.add(poiTag.getKey());
		}
		return tags;
	}
	
	public void end() {
		log.info("Areas2POIHook started");
		addPOIsToWays();
		addPOIsToMPs();
		log.info("Areas2POIHook finished");
	}
	
	private int getPlacementOrder(Element elem) {
		for (int order = 0; order < poiPlacementTags.size(); order++) {
			Entry<String,String> poiTagDef = poiPlacementTags.get(order);
			String tagValue = elem.getTag(poiTagDef.getKey());
			if (tagValue != null) {
				if (poiTagDef.getValue() == null || poiTagDef.getValue().equals(tagValue)) {
					return order;
				}
			}
		}
		// no poi tag match
		return -1;
	}
	
	private void addPOIsToWays() {
		Map<Coord, Integer> labelCoords = new IdentityHashMap<Coord, Integer>(); 
		
		// save all coords with one of the placement tags to a map
		// so that ways use this coord as its labeling point
		if (poiPlacementTags.isEmpty() == false && poisToAreas) {
			for (Node n : saver.getNodes().values()) {
				int order = getPlacementOrder(n);
				if (order >= 0) {
					Integer prevOrder = labelCoords.get(n.getLocation());
					if (prevOrder == null || order < prevOrder.intValue())
						labelCoords.put(n.getLocation(), order);
				}
			}
		}
		
		log.debug("Found", labelCoords.size(), "label coords");
		
		int ways2POI = 0;
		int lines2POI = 0;
		
		for (Way w : saver.getWays().values()) {
			// check if way has any tags
			if (w.getTagCount() == 0) {
				continue;
			}

			// do not add POIs for polygons created by multipolygon processing
			if (w.tagIsLikeYes(MultiPolygonRelation.MP_CREATED_TAG)) {
				if (log.isDebugEnabled())
					log.debug("MP processed: Do not create POI for", w.toTagString());
				continue;
			}
			
			
			// check if it is an area
			if (w.hasIdenticalEndPoints()) {
				if (poisToAreas) {
					addPOItoPolygon(w, labelCoords);
					ways2POI++;
				}
			} else {
				if (poisToLines) {
					lines2POI += addPOItoLine(w);
				}
			}
		}
		
		if (poisToAreas)
			log.info(ways2POI, "POIs from single areas created");
		if (poisToLines)
			log.info(lines2POI, "POIs from lines created");
	}
	
	private void addPOItoPolygon(Way polygon, Map<Coord, Integer> labelCoords) {
		if (poisToAreas == false) {
			return;
		}

		// get the coord where the poi is placed
		Coord poiCoord = null;
		// do we have some labeling coords?
		if (labelCoords.size() > 0) {
			int poiOrder = Integer.MAX_VALUE;
			// go through all points of the way and check if one of the coords
			// is a labeling coord
			for (Coord c : polygon.getPoints()) {
				Integer cOrder = labelCoords.get(c);
				if (cOrder != null && cOrder.intValue() < poiOrder) {
					// this coord is a labeling coord
					// use it for the current way
					poiCoord = c;
					poiOrder = cOrder;
					if (poiOrder == 0) {
						// there is no higher order
						break;
					}
				}
			}
		}
		if (poiCoord == null) {
			// did not find any label coord
			// use the common center point of the area
			poiCoord = polygon.getCofG();
		}
		
		Node poi = createPOI(polygon, poiCoord, AREA2POI_TAG); 
		saver.addNode(poi);
	}
	
	
	private int addPOItoLine(Way line) {
		Node startNode = createPOI(line, line.getPoints().get(0), LINE2POI_TAG);
		startNode.addTag(LINE2POI_TYPE_TAG,"start");
		saver.addNode(startNode);

		Node endNode = createPOI(line, line.getPoints().get(line.getPoints().size()-1), LINE2POI_TAG);
		endNode.addTag(LINE2POI_TYPE_TAG,"end");
		saver.addNode(endNode);

		int noPOIs = 2;
		Coord lastPoint = line.getPoints().get(0);
		if (line.getPoints().size() > 2) {
			for (Coord inPoint : line.getPoints().subList(1, line.getPoints().size()-1)) {
				if (inPoint.equals(lastPoint)){
					continue;
				}
				lastPoint = inPoint;
				Node innerNode = createPOI(line, inPoint, LINE2POI_TAG);
				innerNode.addTag(LINE2POI_TYPE_TAG,"inner");
				saver.addNode(innerNode);
				noPOIs++;
			}
		}
		
		// calculate the middle of the line
		Coord prevC = null;
		double sumDist = 0.0;
		ArrayList<Double> dists = new ArrayList<Double>(line.getPoints().size()-1);
		for (Coord c : line.getPoints()) {
			if (prevC != null) {
				double dist = prevC.distance(c);
				dists.add(dist);
				sumDist+=dist;
			}
			prevC = c;
		}
		
		Coord midPoint = null;
		double remMidDist = sumDist/2;
		for (int midPos =0; midPos < dists.size(); midPos++) {
			double nextDist = dists.get(midPos);
			if (remMidDist <= nextDist) {
				double frac = remMidDist/nextDist;
				midPoint = line.getPoints().get(midPos).makeBetweenPoint(line.getPoints().get(midPos+1), frac);
				break;
			} 
			remMidDist -= nextDist;
		}
		
		if (midPoint != null) {
			Node midNode = createPOI(line, midPoint, LINE2POI_TAG);
			midNode.addTag(LINE2POI_TYPE_TAG,"mid");
			saver.addNode(midNode);
			noPOIs++;
		}
		return noPOIs;

	}

	private static Node createPOI(Element source, Coord poiCoord, short poiTypeTagKey) {
		Node poi = new Node(FakeIdGenerator.makeFakeId(), poiCoord);
		poi.copyTags(source);
		poi.deleteTag(MultiPolygonRelation.STYLE_FILTER_TAG);
		poi.addTag(poiTypeTagKey, "true");
		if (log.isDebugEnabled()) {
			log.debug("Create POI",poi.toTagString(),"from",source.getId(),source.toTagString());
		}
		return poi;
		
	}

	private void addPOIsToMPs() {
		int mps2POI = 0;
		for (Relation r : saver.getRelations().values()) {
			
			// create POIs for multipolygon relations only
			if (r instanceof MultiPolygonRelation == false) {
				continue;
			}
			
			Coord point = ((MultiPolygonRelation)r).getCofG();
			if (point == null) {
				continue;
			}
			
			Node poi = createPOI(r, point, AREA2POI_TAG);
			// remove the type tag which makes only sense for relations
			poi.deleteTag("type");
			saver.addNode(poi);
			mps2POI++;
		}
		log.info(mps2POI,"POIs from multipolygons created");
	}


}
