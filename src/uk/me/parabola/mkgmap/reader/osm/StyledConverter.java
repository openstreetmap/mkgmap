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
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.mkgmap.general.MapCollector;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * An new system to convert from osm styles to garmin styles.  Instead of a
 * single file, there will be multiple files in a directory.  The directory
 * will be the name of the style.  From the start there will be a versioning
 * system, so that we can extend it better.
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {

	private OsmConverter featureConverter;
	private String[] nameTagList;

	public StyledConverter(MapCollector collector, Properties config) throws FileNotFoundException {

		String loc = config.getProperty("style-file");
		String name = config.getProperty("style");
		Style style = new Style(loc, name);

		style.applyOptionOverride(config);
		
		nameTagList = style.getNameTagList();

		try {
			featureConverter = style.makeConverter(collector);
		} catch (IOException e) {
			System.out.println("could not read map-features");
			throw new FileNotFoundException("map features could not be read");
		}
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
		featureConverter.convertWay(way);
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		featureConverter.convertNode(node);
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
