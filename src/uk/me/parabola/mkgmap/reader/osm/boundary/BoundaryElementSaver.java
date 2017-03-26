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
 * This saver only keeps ways or relations with boundaries. Used to prepare the dat for the bounds file.
 * 
 * @author WanMil
 */
public class BoundaryElementSaver extends ElementSaver {
	private static final Logger log = Logger.getLogger(BoundaryElementSaver.class);

	private final BoundarySaver saver;
	private final BoundaryLocationPreparer preparer;
	
	public BoundaryElementSaver(EnhancedProperties args, BoundarySaver saver) {
		super(args);
		this.saver = saver;
		preparer = new BoundaryLocationPreparer(new EnhancedProperties());
	}

	/**
	 * Checks if the given element is an administrative boundary or a
	 * postal code area (or both).
	 * @param element an element
	 * @return <code>true</code> if administrative boundary or postal code; 
	 * <code>false</code> element cannot be used for precompiled bounds 
	 */
	public boolean isBoundary(Element element) {
		if (element instanceof Relation) {
			String type = element.getTag("type");
			if (!"boundary".equals(type) && !"multipolygon".equals(type)) 
				return false;
		} else if (element instanceof Way) {
			Way w = (Way) element;
			// a single way must be closed
			if (w.isClosedInOSM() == false) {
				return false;
			}
		} else {
			return false;
		}
		return hasRelevantTags(element);
	}

	private boolean hasRelevantTags(Element element) {
		BoundaryLocationInfo bInfo = preparer.parseTags(element);
		if (bInfo.getZip() != null)
			return true;
		if (bInfo.getAdmLevel() == BoundaryLocationPreparer.UNSET_ADMIN_LEVEL)
			return false;
		if (bInfo.getName() != null && !"?".equals(bInfo.getName()))
			return true;
		if (bInfo.getAdmLevel() >= 3 && bInfo.getAdmLevel() <= 11) {
			// for admin_level != 2 it is enough when we find a tag key containing "name" (like int_name or name:en)  
			for (Entry<String,String> tag : element.getTagEntryIterator()) {
				if (tag.getKey().contains("name")) {
					return true;
				}
			}
		}
		return false;
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
	}

	public void convert(OsmConverter converter) {
		nodeMap = null;

		converter.setBoundingBox(getBoundingBox());

		ArrayList<Relation> relations = new ArrayList<>(relationMap.values());
		relationMap = null;
		for (int i = 0; i < relations.size(); i++) {
			converter.convertRelation(relations.get(i));
			relations.set(i, null);
		}
		relations = null;

		for (Way w : wayMap.values()) {
			if (isBoundary(w)) {
				converter.convertWay(w);
			}
		}

		wayMap = null;

		converter.end();
	}
}