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

import java.util.HashSet;
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

	private ElementSaver saver;
	
	/** Name of the bool tag that is set to true if a POI is created from an area */
	public static final String AREA2POI_TAG = "mkgmap:area2poi";
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		if (props.containsKey("add-pois-to-areas") == false) {
			log.info("Disable Areas2POIHook because add-pois-to-areas option is not set.");
			return false;
		}
		
		this.saver = saver;
		
		return true;
	}
	
	public void end() {
		log.info("Areas2POIHook started");
		addPOIsToWays();
		addPOIsToMPs();
		log.info("Areas2POIHook finished");
	}
	
	private void addPOIsToWays() {
		Set<Coord> labelCoords = new HashSet<Coord>(); 
		
		// save all coords with building=entrance to a map
		// so that ways use this coord as its labeling point
		for (Node n : saver.getNodes().values()) {
			if ("entrance".equals(n.getTag("building"))) {
				labelCoords.add(n.getLocation());
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
				// go through all points of the way and check if one of the coords
				// is a labeling coord
				for (Coord c : w.getPoints()) {
					if (labelCoords.contains(c)) {
						// this coord is a labeling coord
						// use it for the current way
						poiCoord = c;
						break;
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
