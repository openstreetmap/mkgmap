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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Superclass of the node, segment and way OSM elements.
 */
public abstract class Element implements Iterable<String> {
	private Map<String, String> tags;
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
		if (key.equals("created_by")) {
			// We are attempting to not create a hash map for nodes that are
			// not POI's but just nodes in a way.  At one time 'created_by'
			// was the only common tag that could be ignored, but now there
			// are many such noise tags and we need a better way.  Perhaps
			// we can free the map once we realise that it is not needed.
		} else {
			if (tags == null)
				tags = new HashMap<String, String>();
			tags.put(key, val);
		}
	}

	public String getTag(String key) {
		if (tags == null)
			return null;
		return tags.get(key);
	}

	public Iterator<String> iterator() {
		return new Iterator<String>() {
			private Iterator<Map.Entry<String, String>> tagit;

			{
				if (tags != null)
					tagit = tags.entrySet().iterator();
			}

			public boolean hasNext() {
				return (tagit != null) && tagit.hasNext();
			}

			public String next() {
				Map.Entry<String, String> ent = tagit.next();
				return ent.getKey() + '=' + ent.getValue();
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
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
		for (Map.Entry<String, String> e : tags.entrySet()) {
			sb.append(e.getKey());
			sb.append('=');
			sb.append(e.getValue());
			sb.append(',');
		}
		sb.setLength(sb.length()-1);
		sb.append(']');
		return sb.toString();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
