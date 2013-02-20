/*
 * Copyright (C) 2012.
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


import java.util.Iterator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * A basic memory efficient Map implementation that stores an OSM id with an Object.
 * As of 2012, normal OSM IDs still use only 31 bits, but we must support 64 bits. 
 * This map avoids to store many 0 bits by splitting the id into the upper part where
 * almost all bits are zero and the lower part that changes frequently.
 * @author GerdP
 *
 * @param <V> the type of object that should be stored
 */
public class OSMId2ObjectMap<V> {
	public static final long LOW_ID_MASK = 0x7ffffff;
	public static final long TOP_ID_MASK = ~LOW_ID_MASK;  			// the part of the key that is saved in the top HashMap 
	private static final int TOP_ID_SHIFT = Long.numberOfTrailingZeros(TOP_ID_MASK);

	private Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<V>> topMap;

	private int size;

	public OSMId2ObjectMap() {
		topMap = new Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<V>>();
	}
	
	public V put(long key, V object){
		long topId = key >> TOP_ID_SHIFT;
		Int2ObjectOpenHashMap<V> midMap = topMap.get(topId);
		if (midMap == null){
			midMap = new Int2ObjectOpenHashMap<V>();
			topMap.put(topId, midMap);
		}
		int midId = (int)(key & LOW_ID_MASK);
		V old = midMap.put(midId, object);
		if (old == null)
			size++;
		return old;
	}

	public V get(long key){
		long topId = key >> TOP_ID_SHIFT;
		Int2ObjectOpenHashMap<V> midMap = topMap.get(topId);
		if (midMap == null)
			return null;
		int midId = (int)(key & LOW_ID_MASK);
		return midMap.get(midId);
	}
	public V remove(long key){
		long topId = key >> TOP_ID_SHIFT;
		Int2ObjectOpenHashMap<V> midMap = topMap.get(topId);
		if (midMap == null)
			return null;
		int midId = (int)(key & LOW_ID_MASK);
		V old = midMap.remove(midId);
		if (old == null)
			return null;
		if (midMap.isEmpty())
			topMap.remove(topId);
		size--;
		return old;
	}

	public void clear(){
		topMap.clear();
		size = 0;
	}

	public int size(){
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public boolean containsKey(long key) {
		return get(key) != null; 
	}

}

