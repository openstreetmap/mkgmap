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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Adds a POI for each area and multipolygon with the same tags. Artificial areas created by 
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
 * Each node created by this hook is tagged with mkgmap:area2poi=true.
 * @author WanMil
 */
public class Areas2POIHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(Areas2POIHook.class);

	private List<Entry<String,String>> poiPlacementTags; 
	
	private ElementSaver saver;
	
	/** Name of the bool tag that is set to true if a POI is created from an area */
	public static final String AREA2POI_TAG = "mkgmap:area2poi";
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		if (props.containsKey("add-pois-to-areas") == false) {
			log.info("Disable Areas2POIHook because add-pois-to-areas option is not set.");
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
	private List<Entry<String,String>> getPoiPlacementTags(EnhancedProperties props) {
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
			tags.add(poiTag.getValue());
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
		Map<Coord, Integer> labelCoords = new HashMap<Coord, Integer>(); 
		
		// save all coords with one of the placement tags to a map
		// so that ways use this coord as its labeling point
		if (poiPlacementTags.isEmpty() == false) {
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
		
		for (Way w : saver.getWays().values()) {
			// check if it is an area
			if (w.isClosed() == false) {
				continue;
			}

			if (w.getTagCount() == 0) {
				continue;
			}
			
			// do not add POIs for polygons created by multipolygon processing
			if (w.isBoolTag(MultiPolygonRelation.MP_CREATED_TAG)) {
				log.debug("MP processed: Do not create POI for", w.toTagString());
				continue;
			}
			
			// get the coord where the poi is placed
			Coord poiCoord = null;
			// do we have some labeling coords?
			if (labelCoords.size() > 0) {
				int poiOrder = Integer.MAX_VALUE;
				// go through all points of the way and check if one of the coords
				// is a labeling coord
				for (Coord c : w.getPoints()) {
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
				poiCoord = w.getCofG();
			}
			
			Node poi = new Node(FakeIdGenerator.makeFakeId(), poiCoord);
			poi.copyTags(w);
			poi.deleteTag(MultiPolygonRelation.STYLE_FILTER_TAG);
			poi.addTag(AREA2POI_TAG, "true");
			log.debug("Create POI",poi.toTagString(),"from",w.getId(),w.toTagString());
			saver.addNode(poi);
			ways2POI++;
		}
		
		log.info(ways2POI, "POIs from single areas created");
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
			
			Node poi = new Node(FakeIdGenerator.makeFakeId(), point);
			poi.copyTags(r);
			// remove the type tag which makes only sense for relations
			poi.deleteTag("type");
			poi.addTag(AREA2POI_TAG, "true");
			log.debug("Create POI",poi.toTagString(),"from mp",r.getId(),r.toTagString());
			saver.addNode(poi);
			mps2POI++;
		}
		log.info(mps2POI, "POIs from multipolygons created");
	}


}
