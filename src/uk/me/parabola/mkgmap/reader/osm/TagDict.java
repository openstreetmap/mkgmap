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

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import uk.me.parabola.imgfmt.MapFailedException;


/**
 * A dictionary for tag names. Allows to translate a tag name to a unique Short value.
 * @author GerdP
 *
 */
public class TagDict{
	private final static TagDict INSTANCE = new TagDict();
	private final HashMap<String,Short>  map = new HashMap<>();
	private final ArrayList<String>  list = new ArrayList<>();

	public static final short INVALID_TAG_VALUE = 0;

	/**
	 * create an empty dictionary
	 */
	private TagDict() {
		map.put("invalid tag", INVALID_TAG_VALUE);
		list.add("invalid tag");
	}
	
	/** 
	 * give access to the singleton instance  
	 * @return
	 */
	public static TagDict getInstance() {
		return INSTANCE;
	}		
	
	/**
	 * translate a tag name to a short value
	 * @param keyString the tag name
	 * @return a Short > 0 that can be used to retrieve
	 * the tag name with the get() method
	 */
	public synchronized  short xlate (String keyString){
		Short tagKey = map.get(keyString);
		if (tagKey == null) {
			short size = (short) list.size();
			if (size == Short.MAX_VALUE){
				// very unlikely, typically we have a few hundred tag names
				throw new MapFailedException("Fatal: Too many different tags in style");
			}
			String s = keyString;
			map.put(s, size);
			//System.out.println(""+x + ":" +   s);
			list.add(s);
			return size;
		}
		return tagKey.shortValue();
	}

	/**
	 * get the tagName for a tagKey. The caller has
	 * to make sure that the key is valid.
	 * @param key the tagKey (returned by xlate()
	 * @return the tagName
	 */
	public String get(short key){
		if (key == INVALID_TAG_VALUE) return null;
			
		return list.get(key);
	}
	
	/**
	 * The size of the dictionary. The highest known tagKey is 
	 * size() - 1. 
	 * @return 
	 */
	public int size(){
		return list.size();
	}
	
	/**
	 * Return list of compiled tag keys for given array of key strings.
	 * @param keys the keys
	 * @return a ShortArrayList which might be empty but will not be null.
	 */
	public static ShortArrayList compileTags(String ...keys) {
		ShortArrayList compiled = new ShortArrayList();
		if (keys != null) {
			for (String key : keys) {
				compiled.add(getInstance().xlate(key));
			}
		}
		return compiled;
	}
}
