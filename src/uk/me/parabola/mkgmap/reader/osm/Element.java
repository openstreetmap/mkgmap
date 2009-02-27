/**
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Author: steve
 * Date: 26-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Collections;
import java.util.Iterator;

/**
 * Superclass of the node, segment and way OSM elements.
 */
public abstract class Element implements Iterable<String> {
	private Tags tags;
	private String name;
	private long id;

	/**
	 * Add a tag to the way.  Some tags are recognised separately and saved in
	 * separate fields.
	 *
	 * @param key The tag name.
	 * @param val Its value.
	 */
	public void addTag(String key, String val) {
		if (tags == null)
			tags = new Tags();
		tags.reallyPut(key, val);
	}

	public String getTag(String key) {
		if (tags == null)
			return null;
		return tags.get(key);
	}

	public void deleteTag(String tagname) {
		tags.remove(tagname);
	}

	public Iterator<String> iterator() {
		if (tags == null) 
			return Collections.<String>emptyList().iterator();

		return tags.iterator();
	}

	public long getId() {
		return id;
	}

	protected void setId(long id) {
		this.id = id;
	}

	protected String toTagString() {
		if (tags == null)
			return "";

		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (String nameval : tags) {
			sb.append(nameval);
			sb.append(',');
		}
		sb.setLength(sb.length()-1);
		sb.append(']');
		return sb.toString();
	}

	/**
	 * Copy the tags of the other element.  Only to be used internally
	 * by subclasses.
	 * @param other The other element.  All its tags will be copied to this
	 * element.
	 */
	public void copyTags(Element other) {
		tags = other.tags.copy();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (this.name == null)
			this.name = name;
	}
}
