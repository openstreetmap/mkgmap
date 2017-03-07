/*
 * Copyright (C) 2017.
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
package uk.me.parabola.mkgmap.osmstyle;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.reader.osm.Tags;

/**
 * Class to handle the option name-tag-list
 * @author Gerd Petermann
 *
 */
public class NameFinder {
	private final ShortArrayList compiledNameTagList;
	
	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern.compile("[,\\s]+");
	private static final short nameTagKey = TagDict.getInstance().xlate("name");  
	
	public NameFinder(Properties props) {
		this.compiledNameTagList = computeCompiledNameTags(props);
	}

	public static List<String> getNameTags(Properties props) {
		String nameTagProp = props.getProperty("name-tag-list", "name");
		return Arrays.asList(COMMA_OR_SPACE_PATTERN.split(nameTagProp));
	}

	/**
	 * Analyse name-tag-list option.
	 * @param props program properties
	 * @return list of compiled tag keys or null if only name should be used.
	 */
	private static ShortArrayList computeCompiledNameTags(Properties props) {
		if (props == null)
			return null;
		String nameTagProp = props.getProperty("name-tag-list", "name");
		if ("name".equals(nameTagProp))
			return null;
		return TagDict.compileTags(COMMA_OR_SPACE_PATTERN.split(nameTagProp));
	}
	
	
	/**
	 * Get name tag value according to name-tag-list.
	 * @param el
	 * @return the tag value or null if none of the name tags is set. 
	 */
	public String getName(Element el) {
		if (compiledNameTagList == null)
			return el.getTag(nameTagKey);

		for (short tagKey : compiledNameTagList) {
			String val = el.getTag(tagKey);
			if (val != null) {
				return val;
			}
		}
		return null;
	}

	/**
	 * Get name tag value according to name-tag-list.
	 * @param tags the tags to check
	 * @return the tag value or null if none of the name tags is set. 
	 */
	public String getName(Tags tags) {
		if (compiledNameTagList == null)
			return tags.get(nameTagKey);

		for (short tagKey : compiledNameTagList) {
			String val = tags.get(tagKey);
			if (val != null) {
				return val;
			}
		}
		return null;
	}
	
	/**
	 * Use name-tag-list to set the name tag for the element. 
	 * @param el the element
	 */
	public void setNameWithNameTagList(Element el) {
		if (compiledNameTagList == null)
			return;

		for (short tagKey : compiledNameTagList) {
			String val = el.getTag(tagKey);
			if (val != null) {
				if (tagKey != nameTagKey) {
					// add or replace name 
					el.addTag(nameTagKey, val);
				}
				break;
			}
		}
		
	}
}
