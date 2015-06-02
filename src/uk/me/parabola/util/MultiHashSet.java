/*
 * Copyright (C) 2015 Gerd Petermann
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
package uk.me.parabola.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;


public class MultiHashSet<K,V> extends HashMap<K,Set<V>> {

	/**
	* the empty set to be returned when there is key without values.
	*/
	private final Set<V> emptyList = Collections.emptySet();

	/**
	* Returns the list of values associated with the given key.
	*
	* @param key the key to get the values for.
	* @return a list of values for the given keys or the empty list of no such
	*         value exist.
	*/
	public Set<V> get(Object key) {
		Set<V> result = super.get(key);
		return result == null ? emptyList : result;
	}


	public boolean add(K key, V value ) {
		Set<V> values = super.get(key);
	    if (values == null ) {
	        values = new LinkedHashSet<V>();
	        super.put( key, values );
	    }
	    return values.add(value);
	}

	public boolean removeMapping(K key, V value) {
	    Set<V> values = super.get(key);
	    if (values == null )
			return false;

	    boolean existed = values.remove(value);
		
		if (values.isEmpty())
			super.remove(key);

		return existed;
	}
}

