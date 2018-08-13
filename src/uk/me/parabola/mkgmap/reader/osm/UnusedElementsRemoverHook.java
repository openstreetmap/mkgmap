/*
 * Copyright (C) 2012.
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * The hook removes all elements that will not be included in the map and can therefore
 * be safely removed. This improves the performance because the elements do not have
 * to go through the style system. 
 * 
 * @author WanMil
 */
public class UnusedElementsRemoverHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(UnusedElementsRemoverHook.class);

	private ElementSaver saver;
	
	/** node with tags of this list must not be removed */
	private List<Entry<String,String>> areasToPoiNodeTags;
	
	public UnusedElementsRemoverHook() {
	}

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		
		// Get the tags from the POIGeneratorHook which are used to define the point
		// where the POI is placed in polygons. They must not be removed if the polygon
		// is not removed. Checking if the polygon is not removed is too costly therefore
		// all nodes with these tags are kept.
		if (props.containsKey("add-pois-to-areas"))
			areasToPoiNodeTags = POIGeneratorHook.getPoiPlacementTags(props);
		else 
			areasToPoiNodeTags = new ArrayList<>();
		return true;
	}
	
	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Removing unused elements");
		
		final Area bbox = saver.getBoundingBox();
		
		long nodes = saver.getNodes().size();

		// go through all nodes 
		for (Node node : new ArrayList<Node>(saver.getNodes().values())) {

			// nodes without tags can be removed
			if (node.getTagCount() == 0) {
				saver.getNodes().remove(node.getId());
				continue;
			}
			
			// check if the node is within the tile bounding box 
			if (bbox.contains(node.getLocation()) == false) {
				boolean removeNode = true;
				
				for (Entry<String, String> tag : areasToPoiNodeTags) {
					// check if the node has a tag used by the POIGeneratorHook
					String val = node.getTag(tag.getKey());
					if (val != null) {
						if (tag.getValue() == null || val.equals(tag.getValue())) {
							// the node contains one tag that might be
							// interesting for the POIGeneratorHook
							// do not remove it
							removeNode = false;
							break;
						}
					}
				}
				if (removeNode) {
					saver.getNodes().remove(node.getId());
				} else {
					log.debug("Keep node", node, "because it contains a tag which might be required for the area-to-poi function.");
				}
			}
		}
		
		Rectangle bboxRect = new Rectangle(bbox.getMinLong(), bbox.getMinLat(), bbox.getWidth(), bbox.getHeight());
		long ways = saver.getWays().size();
		for (Way way : new ArrayList<Way>(saver.getWays().values())) {
			if (way.isViaWay())
				continue;
			if (way.getPoints().isEmpty()) {
				// empty way will not appear in the map => remove it
				saver.getWays().remove(way.getId());
				continue;
			}
			
			// check if a way has no tags
			// it is presumed that the RelationStyleHook was already executed
			if (way.getTagCount() == 0) {
				saver.getWays().remove(way.getId());
				continue;
			}
			
			// check if the way is completely outside the tile bounding box
			boolean coordInBbox = false;
			Coord prevC = null;
			
			// It is possible that the way is larger than the bounding box and therefore 
			// contains the bbox completely. Especially this is true for the sea polygon
			// when using --generate-sea=polygon
			for (Coord c : way.getPoints()) {
				if (bbox.contains(c)) {
					coordInBbox = true;
					break;
				} else if (prevC != null) {
					// check if the line intersects the bounding box
					if (bboxRect.intersectsLine(prevC.getLongitude(), prevC.getLatitude(), c.getLongitude(), c.getLatitude())) {
						if (log.isDebugEnabled()) {
							log.debug("Intersection!");
							log.debug("Bbox:", bbox);
							log.debug("Way coords:", prevC, c);
						}
						coordInBbox = true;
						break;
					}
				}
				
				prevC = c;
			}
			if (coordInBbox==false) {
				// no coord of the way is within the bounding box
				// check if the way possibly covers the bounding box completely
				Area wayBbox = Area.getBBox(way.getPoints());
				if (wayBbox.contains(saver.getBoundingBox())) {
					log.debug(way, "possibly covers the bbox completely. Keep it.");
				} else {
					saver.getWays().remove(way.getId());
				}
			} 
		}
		
		log.info("Nodes: before:", nodes, "after:", saver.getNodes().size());	
		log.info("Ways: before:", ways, "after:", saver.getWays().size());	
		log.info("Removing unused elements took", (System.currentTimeMillis()-t1), "ms");
	}
}
