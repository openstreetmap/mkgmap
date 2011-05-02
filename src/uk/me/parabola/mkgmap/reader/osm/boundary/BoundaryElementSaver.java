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

	private final BoundarySaver saver;
	
	public BoundaryElementSaver(EnhancedProperties args, BoundarySaver saver) {
		super(args);
		this.saver = saver;
	}

	public static boolean isBoundary(Element element) {
		if (element instanceof Relation) {
			String type = element.getTag("type");
			if ("boundary".equals(type)) {
				return true;
			}
			if ("multipolygon".equals(type)
					&& "administrative".equals(element.getTag("boundary"))) {
				return true;
			}
			return false;
		} else if (element instanceof Way) {
			return "administrative".equals(element.getTag("boundary"));
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