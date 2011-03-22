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
import java.util.Map;

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
		tags.put(key, val);
	}

	public String getTag(String key) {
		if (tags == null)
			return null;
		return tags.get(key);
	}

	public void deleteTag(String tagname) {
		if(tags != null) {
			tags.remove(tagname);
			if (tags.size() == 0) {
				tags = null;
			}
		}
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

	public String toTagString() {
		if (tags == null)
			return "[]";

		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (String nameval : tags) {
			sb.append(nameval);
			sb.append(',');
		}
		if (sb.length() > 1) {
			sb.setLength(sb.length()-1);
		}
		sb.append(']');
		return sb.toString();
	}

	/**
	 * Copy the tags of the other element which replaces all tags of this element.
	 *   
	 * @param other The other element.  All its tags will be copied to this
	 * element.
	 */
	public void copyTags(Element other) {
		if (other.tags == null)
			tags = null;
		else
			tags = other.tags.copy();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (this.name == null)
			this.name = name;
	}

	public Map<String, String> getTagsWithPrefix(String prefix, boolean removePrefix) {
		if (tags == null) 
			return Collections.emptyMap();
		
		return tags.getTagsWithPrefix(prefix, removePrefix);
	}

	protected void removeAllTags() {
		tags = null;
	}

	public Iterable<Map.Entry<String, String>> getEntryIteratable() {
		return new Iterable<Map.Entry<String, String>>() {
			public Iterator<Map.Entry<String, String>> iterator() {
				if (tags == null)
					return Collections.<String, String>emptyMap().entrySet().iterator();
				else
					return tags.entryIterator();
			}
		};
	}

	protected String kind() {
		return "unknown";
	}

	public String toBrowseURL() {
		return "http://www.openstreetmap.org/browse/" + kind() + "/" + id;
	}

	public Element copy() {
		// Can be implemented in subclasses
		throw new UnsupportedOperationException("unsupported element copy");
	}
}
