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
import java.util.Collections;
import java.util.Map.Entry;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.ElementSaver;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This saver only keeps ways with <code>natural=coastline</code> tags. This is
 * used for loading of extra coastline files.
 * 
 * @author WanMil
 */
public class BoundaryElementSaver extends ElementSaver {
	private static final Logger log = Logger.getLogger(BoundaryElementSaver.class);

	private final BoundarySaver saver;
	
	public BoundaryElementSaver(EnhancedProperties args, BoundarySaver saver) {
		super(args);
		this.saver = saver;
	}

	/**
	 * Checks if the given element is an administrative boundary or a
	 * postal code area.
	 * @param element an element
	 * @return <code>true</code> administrative boundary or postal code; 
	 * <code>false</code> element cannot be used for precompiled bounds 
	 */
	public static boolean isBoundary(Element element) {
		if (element instanceof Relation) {
			String type = element.getTag("type");
			
			if ("boundary".equals(type) || "multipolygon".equals(type)) {
				String boundaryVal = element.getTag("boundary");
				if ("administrative".equals(boundaryVal)) {
					// for boundary=administrative the admin_level must be set
					if (element.getTag("admin_level") == null) {
						return false;
					}
					// and a name must be set (check only for a tag containing name
					for (Entry<String,String> tag : element.getEntryIteratable()) {
						if (tag.getKey().contains("name")) {
							return true;
						}
					}
					// does not contain a name tag => do not use it
					return false;					
				} else if ("postal_code".equals(boundaryVal)) {
					// perform a positive check
					
					// is postal_code set?
					if (element.getTag("postal_code") != null) {
						return true;
					}
					// and a name must be set (check only for a tag containing name
					for (Entry<String,String> tag : element.getEntryIteratable()) {
						if (tag.getKey().contains("name")) {
							return true;
						}
					}
					// does not contain a name tag => do not use it
					return false;						
				} else if (element.getTag("postal_code") != null){
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		} else if (element instanceof Way) {
			Way w = (Way) element;
			// a single way must be closed
			if (w.isClosed() == false) {
				return false;
			}
			// the boundary tag must be "administrative" or "postal_code"
			String boundaryVal = element.getTag("boundary");
			if ("administrative".equals(boundaryVal)) {
				// for boundary=administrative the admin_level must be set
				if (element.getTag("admin_level") == null) {
					return false;
				}
				
				// and a name must be set (check only for a tag containing name)
				for (Entry<String,String> tag : element.getEntryIteratable()) {
					if (tag.getKey().contains("name")) {
						return true;
					}
				}
				// does not contain a name tag => do not use it
				return false;
			} else if ( "postal_code".equals(boundaryVal)) {
				// the name tag must be set for it
				return element.getTag("name") != null;
			} else if (element.getTag("postal_code") != null) {
				// postal_code as tag
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public void addRelation(Relation rel) {
		if (isBoundary(rel)) {
			BoundaryRelation bRel = (BoundaryRelation) createMultiPolyRelation(rel);
			bRel.processElements();
			Boundary b = bRel.getBoundary();
			if (b != null)
				saver.addBoundary(b);
		} else {
			log.warn("Relation is not processed due to missing tags:", rel.getId(), rel.toTagString());
		}
	}
	
	public void deferRelation(long id, Relation rel, String role) {
		return;
	}
	
	public Relation createMultiPolyRelation(Relation rel) {
		return new BoundaryRelation(rel, wayMap, getBoundingBox());
	}

	public void addNode(Node node) {
		return;
	}

	public void convert(OsmConverter converter) {
		nodeMap = null;

		converter.setBoundingBox(getBoundingBox());

		ArrayList<Relation> relations = new ArrayList<Relation>(
				relationMap.values());
		relationMap = null;
		Collections.reverse(relations);
		for (int i = relations.size() - 1; i >= 0; i--) {
			converter.convertRelation(relations.get(i));
			relations.remove(i);
		}


		for (Way w : wayMap.values())
			converter.convertWay(w);

		wayMap = null;

		converter.end();

		relationMap = null;
	}
}