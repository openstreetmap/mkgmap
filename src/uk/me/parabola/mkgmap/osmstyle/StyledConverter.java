/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.reader.osm.TypeRule;
import uk.me.parabola.mkgmap.reader.osm.GType;

/**
 * Convert from OSM to the mkgmap intermediate format using a style.
 * A style is a collection of files that describe the mappings to be used
 * when converting.
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(StyledConverter.class);

	private final String[] nameTagList;

	private Map<String, TypeRule> wayValueRules = new HashMap<String, TypeRule>();
	//private Map<String, GType> wayRules = new HashMap<String, GType>();
	private Map<String, TypeRule> nodeValueRules = new HashMap<String, TypeRule>();
	//private Map<String, GType> nodeRules = new HashMap<String, GType>();
	private final MapCollector collector;

	public StyledConverter(Style style, MapCollector collector) {
		this.collector = collector;

		nameTagList = style.getNameTagList();

		wayValueRules = style.getWays();
		nodeValueRules = style.getNodes();
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		GType foundType = null;
		for (String tagKey : way) {
            TypeRule rule = wayValueRules.get(tagKey);
			if (rule != null) {
				GType gt = rule.resolveType(way);
				if (gt != null && (foundType == null || gt.isBetter(foundType)))
					foundType = gt;
			}
		}

		if (foundType == null)
			return;

		// If the way does not have a name, then set the name from this
		// type rule.
		if (way.getName() == null)
			way.setName(foundType.getDefaultName());

		if (foundType.getFeatureKind() == GType.POLYLINE)
            addLine(way, foundType);
		else
			addShape(way, foundType);
	}

	private void addLine(Way way, GType gt) {
		MapLine ms = new MapLine();
		elementSetup(ms, gt, way);
		ms.setPoints(way.getPoints());

		collector.addLine(ms);
	}

	private void addShape(Way way, GType gt) {
		MapShape ms = new MapShape();
		elementSetup(ms, gt, way);
		ms.setPoints(way.getPoints());

		collector.addShape(ms);
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		GType foundType = null;
		for (String tagKey : node) {
			TypeRule rule = nodeValueRules.get(tagKey);
			if (rule != null) {
				GType gt = rule.resolveType(node);
				if (gt != null && (foundType == null || gt.isBetter(foundType)))
					foundType = gt;
			}
		}

		if (foundType == null)
			return;

		// If the node does not have a name, then set the name from this
		// type rule.
		log.debug("node name", node.getName());
		if (node.getName() == null) {
			node.setName(foundType.getDefaultName());
			log.debug("after set", node.getName());
		}

		addPoint(node, foundType);
	}

	private void addPoint(Node node, GType gt) {
		MapPoint mp = new MapPoint();
		elementSetup(mp, gt, node);
		mp.setSubType(gt.getSubtype());
		mp.setLocation(node.getLocation());

		collector.addPoint(mp);
	}

	private void elementSetup(MapElement ms, GType gt, Element element) {
		ms.setName(element.getName());
		ms.setType(gt.getType());
		ms.setMinResolution(gt.getMinResolution());
		ms.setMaxResolution(gt.getMaxResolution());
	}

	/**
	 * Set the name of the element.  Usually you will just take the name
	 * tag, but there are cases where you may want to use other tags, eg the
	 * 'ref' tag for roads.
	 *
	 * @param el The element to set the name upon.
	 */
	public void convertName(Element el) {
		String ref = el.getTag("ref");
		String name = getName(el);
		if (name == null) {
			el.setName(ref);
		} else if (ref != null) {
			StringBuffer ret = new StringBuffer(name);
			ret.append(" (");
			ret.append(ref);
			ret.append(')');
			el.setName(ret.toString());
		} else {
			el.setName(name);
		}
	}

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it. All elements that are added
	 * are clipped to this box, new points are added as needed at the boundry.
	 *
	 * If a node or a way falls completely outside the boundry then it would be
	 * ommited.  This would not normally happen in the way this option is typically
	 * used however.
	 *
	 * @param bbox The bounding area.
	 */
	public void setBoundingBox(Area bbox) {
		//featureConverter.setBoundingBox(bbox);
		//TODO: make this work
	}

	/**
	 * Get the name tag. By default you get the tag called 'name', but
	 * for special purposes you may want to provide a list of tag-names
	 * to try.  In particular this allows you to select language specific
	 * versions of the names.  eg. name:cy, name
	 * @param el The element we want to get the name tag from.
	 * @return The value of the defined 'name' tag.
	 */
	private String getName(Element el) {
		if (nameTagList == null)
			return el.getTag("name");

		for (String t : nameTagList) {
			String val = el.getTag(t);
			if (val != null)
				return val;
		}
		return null;
	}
}
