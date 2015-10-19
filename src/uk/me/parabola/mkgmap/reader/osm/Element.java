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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.log.Logger;


/**
 * Superclass of the node, segment and way OSM elements.
 */
public abstract class Element {
	private static final Logger log = Logger.getLogger(Element.class);
	
	private Tags tags;
	private long id;
	private long originalId;

	public int getTagCount() {
		return (tags == null ? 0 : tags.size());
	}
	
	/**
	 * Add a tag to the element. This method should be called by OSM readers
	 * because it trims obsolete spaces from the value.
	 *
	 * @param key The tag name.
	 * @param val Its value.
	 */
	public void addTagFromRawOSM(String key, String val) {
		if (val != null){
			val = val.trim();
			if (val.isEmpty() == false){
				// remove duplicated spaces within value
				String squashed = Label.squashSpaces(val);
				if (val.equals(squashed) == false) {
					if (log.isInfoEnabled())
						log.info(this.toBrowseURL(),"obsolete blanks removed from tag", key, " '" + val + "' -> '" + squashed + "'");
					val = squashed;
				}
			}
		}
		addTag(key, val.intern());
	}

	/**
	 * Add a tag to the element.  Some tags are recognised separately and saved in
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

	/**
	 * Add a tag to the element.  Some tags are recognised separately and saved in
	 * separate fields.
	 *
	 * @param tagKey The tag id created by TagDict
	 * @param val Its value.
	 */
	public void addTag(short tagKey, String val) {
		if (tags == null)
			tags = new Tags();
		tags.put(tagKey, val);
	}

	public String getTag(String key) {
		if (tags == null)
			return null;
		return tags.get(key);
	}
	public String getTag(short tagKey) {
		if (tags == null)
			return null;
		return tags.get(tagKey);
	}


	public String deleteTag(String tagname) {
		String old = null;
		if(tags != null) {
			old = tags.remove(tagname);
			if (tags.size() == 0) {
				tags = null;
			}
			
		}
		return old;
	}

	public String deleteTag(short tagKey) {
		String old = null;
		if(tags != null) {
			old = tags.remove(tagKey);
			if (tags.size() == 0) {
				tags = null;
			}
			
		}
		return old;
	}

	/**
	 * Retrieves if the given tag has a "positive" boolean value which means its value is
	 * one of
	 * <ul>
	 * <li><code>true</code></li>
	 * <li><code>yes</code></li>
	 * <li><code>1</code></li>
	 * </ul>
	 * @param s tag name
	 * @return <code>true</code> if the tag value is a boolean tag with a "positive" value
	 */
	public boolean tagIsLikeYes(String s) {
		return tagIsLikeYes(TagDict.getInstance().xlate(s));
	}

	/**
	 * Retrieves if the given tag has a "positive" boolean value which means its value is
	 * one of
	 * <ul>
	 * <li><code>true</code></li>
	 * <li><code>yes</code></li>
	 * <li><code>1</code></li>
	 * </ul>
	 * @param tagKey tag id returned by TagDict
	 * @return <code>true</code> if the tag value is a boolean tag with a "positive" value
	 */
	public boolean tagIsLikeYes(short tagKey) {
		String val = getTag(tagKey);
		if (val == null)
			return false;

		if (val.equals("yes") || val.equals("true") ||  val.equals("1"))
			return true;

		return false;
	}

	/**
	 * Retrieves if the given tag has a "negative" boolean value which means its value is
	 * one of
	 * <ul>
	 * <li><code>false</code></li>
	 * <li><code>no</code></li>
	 * <li><code>0</code></li>
	 * </ul>
	 * @param s tag name
	 * @return <code>true</code> if the tag value is a boolean tag with a "negative" value
	 */
	public boolean tagIsLikeNo(String s) {
		return tagIsLikeNo(TagDict.getInstance().xlate(s));
	}
	
	/**
	 * Retrieves if the given tag has a "negative" boolean value which means its value is
	 * one of
	 * <ul>
	 * <li><code>false</code></li>
	 * <li><code>no</code></li>
	 * <li><code>0</code></li>
	 * </ul>
	 * @param tagKey tag id returned by TagDict
	 * @return <code>true</code> if the tag value is a boolean tag with a "negative" value
	 */
	public boolean tagIsLikeNo(short tagKey) {
		String val = getTag(tagKey);
		if (val == null)
			return false;

		if (val.equals("no") || val.equals("false") || val.equals("0"))
			return true;

		return false;
	}
	
	public long getId() {
		return id;
	}

	/**
	 * Returns the Id of the original OSM element on which this element was based.
	 * <p>
	 * The Id of the original element will be different from the Id of this
	 * element if this element uses a faked Id.
	 */
	public long getOriginalId() {
		return originalId;
	}

	protected void setId(long id) {
		this.id = id;
		originalId = id;
	}

	public void setFakeId() {
		id = FakeIdGenerator.makeFakeId();
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
		return getTag("mkgmap:label:1");
	}

	public Map<String, String> getTagsWithPrefix(String prefix, boolean removePrefix) {
		if (tags == null) 
			return Collections.emptyMap();
		
		return tags.getTagsWithPrefix(prefix, removePrefix);
	}

	protected void removeAllTags() {
		tags = null;
	}

	/**
	 * @return a Map iterator for the key + value pairs  
	 */
	
	public Iterable<Map.Entry<String, String>> getTagEntryIterator() {
		return new Iterable<Map.Entry<String, String>>() {
			public Iterator<Map.Entry<String, String>> iterator() {
				if (tags == null)
					return Collections.emptyIterator();
				return tags.entryIterator();
			}
		};
	}

	/**
	 * @return a Map iterator for the key + value pairs  
	 */
	public Iterable<Map.Entry<Short, String>> getFastTagEntryIterator() {
		return new Iterable<Map.Entry<Short, String>>() {
			public Iterator<Map.Entry<Short, String>> iterator() {
				if (tags == null)
					return Collections.emptyIterator();
				return tags.entryShortIterator();
			}
		};
	}

	protected String kind() {
		return "unknown";
	}

	public String toBrowseURL() {
		return "http://www.openstreetmap.org/" + kind() + "/" + id;
	}

	public Element copy() {
		// Can be implemented in subclasses
		throw new UnsupportedOperationException("unsupported element copy");
	}
	
	public String getDebugName() {
		String name = getName();
		if(name == null)
			name = getTag("ref");
		if(name == null)
			name = "";
		else
			name += " ";
		return name + "(OSM id " + getId() + ")";
	}
}
