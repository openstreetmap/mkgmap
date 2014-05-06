/*
 * Copyright (C) 2014.
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
package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * A dictionary for tag names. Allows to translate a tag name to a unique Short value.
 * @author GerdP
 *
 */
public class TagDict{
	private static TagDict instance = null;
	private HashMap<String,Short>  map;
	private ArrayList<String>  list;

	public static final short INVALID_TAG_VALUE = 0;

	/**
	 * create an empty dictionary
	 */
	private TagDict() {
		map = new HashMap<String,Short>();
		list = new ArrayList<String>();
		map.put("invalid tag", INVALID_TAG_VALUE);
		list.add("invalid tag");
	}
	
	/** 
	 * give access to the singleton instance  
	 * @return
	 */
	public static synchronized TagDict getInstance() {
		if (instance == null) {
			instance = new TagDict();
		}
		return instance;
	}		
	
	/**
	 * translate a tag name to a short value
	 * @param keyString the tag name
	 * @return a Short > 0 that can be used to retrieve
	 * the tag name with the get() method
	 */
	public synchronized  Short xlate (String keyString){
		Short tagKey = map.get(keyString);
		if (tagKey == null) {
			Short size = (short) (list.size());
			if (size == Short.MAX_VALUE){
				// very unlikely, typically we have a few hundred tag names
				System.err.println("Fatal: Too many different tags");
				System.exit(-1);
			}
			String s = keyString;
			map.put(s, size);
			//System.out.println(""+x + ":" +   s);
			list.add(s);
			return size;
		}
		return tagKey;
	}

	/**
	 * get the tagName for a tagKey. The caller has
	 * to make sure that the key is valid.
	 * @param key the tagKey (returned by xlate()
	 * @return the tagName
	 */
	public String get(Short key){
		if (key == INVALID_TAG_VALUE) return null;
			
		return list.get(key);
	}
	
	/**
	 * translate a List of tags to an array of tagKeys
	 * @param tags the list of tag names
	 * @return the array with tagKeys in the order of the input list
	 */
	public Short[] toArray (List<String> tags){
		if (tags == null)
			return null;
		Short [] a = new Short[tags.size()];
		int i=0;
		for (String s:tags){
			a[i++] = TagDict.getInstance().xlate(s);
		}
		return a;
	}
	/**
	 * The size of the dictionary. The highest known tagKey is 
	 * size() - 1. 
	 * @return 
	 */
	public int size(){
		return list.size();
	}
}
