/*
 * Copyright (C) 2008 - 2012.
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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/**
 * Store the tags that belong to an Element.
 *
 * Used to use a HashMap for this.  We used to have a requirement to be able
 * to add to the map during iteration over it but now the main reason
 * to keep this class is that it is more memory efficient than a regular
 * HashMap (hash maps are the main use of memory in the
 * application), as it doesn't allocate a Map.Entry object for every tag.
 * Performance of the whole application is unchanged compared with when
 * a regular HashMap was used.
 *
 * It doesn't fully behave the same way that a map would.
 *
 * @author Steve Ratcliffe
 */
public class Tags {
	private static final int INIT_SIZE = 8;
	private static final TagDict tagDict = TagDict.getInstance();  

	private short keySize;
	private short capacity;
	
	private short size;

	private short[] keys;
	private String[] values;

	public Tags() {
		keys = new short[INIT_SIZE];
		values = new String[INIT_SIZE];
		capacity = INIT_SIZE;
	}

	public String get(String key) {
		short k = tagDict.xlate(key);
		int ind = keyPos(k);
		if (ind < 0)
			return null;

		return values[ind];
	}
	
	public String get(short key) {
		int ind = keyPos(key);
		
		if (ind < 0)
			return null;

		return values[ind];
	}
	
	/**
	 * Retrieves the number of tags.
	 * @return number of tags
	 */
	public int size() {
		return size;
	}

	public String put(String key, String value) {
		assert key != null : "key is null";
		short dictIdx = tagDict.xlate(key);
		return put(dictIdx,value);
	}

	public String put(short key, String value) {
		assert value != null : "value is null";
		ensureSpace();
		int ind = keyPos(key);
		if (ind < 0)
			assert false : "keyPos(" + key + ") returns null - size = " + keySize + ", capacity = " + capacity;
		keys[ind] = key;

		String old = values[ind];
		if (old == null) {
			keySize++;
			size++;
		}
		values[ind] = value;

		return old;
	}

	public String remove(short key) {
		int k = keyPos(key);

		if (k >= 0 && values[k] != null) {
			// because of the way this works, you can never remove keys
			// except when resizing.
			String old = values[k];
			values[k] = null;
			size--;
			return old;
		}
		return null;
	}
	
	public String remove(String key) {
		short kd = tagDict.xlate(key);
		return remove(kd); 
	}
	
	/**
	 * Make a deep copy of this object.
	 * @return A copy of this object.
	 */
	public Tags copy() {
		Tags cp = new Tags();
		cp.keySize = keySize;
		cp.size = size;
		cp.capacity = capacity;

		cp.keys = Arrays.copyOf(keys, keys.length);
		cp.values = Arrays.copyOf(values, values.length);
		return cp;
	}

	private void ensureSpace() {
		while (keySize + 1 >= capacity) {
			short ncap = (short) (capacity*2);
			short[] okey = keys;
			String[] oval = values;
			keys = new short[ncap];
			values = new String[ncap];
			capacity = ncap;
			keySize = 0;
			size = 0;
			for (int i = 0; i < okey.length; i++) {
				short k = okey[i];
				String v = oval[i]; // null if tag has been removed
				if (k != TagDict.INVALID_TAG_VALUE && v != null){
					//put(keyDict.get(k), v);
					int ind = keyPos(k);
					keys[ind] = k;
					values[ind] = v;
					++keySize;
					++size;
				}
			}
		}
		assert keySize < capacity;
	}

	private int keyPos(short key) {
		int k = key & (capacity - 1);

		int i = k;
		do {
			if (keys[i] == TagDict.INVALID_TAG_VALUE || keys[i] == key)
				return i;
			i++;
			if (i >= capacity)
				i = 0;
		} while (i != k);
		return -1;
	}

	public Iterator<Map.Entry<String, String>> entryIterator() {
		return new Iterator<Map.Entry<String, String>>() {
			private int pos;
			private int done;

			public boolean hasNext() {
				return done < size;
			}

			public Map.Entry<String, String> next() {
				if (done >= size)
					throw new NoSuchElementException();

				for (; pos < capacity; pos++) {
					if (values[pos] != null) {
						break;
					}
				}
				Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<>(tagDict.get(keys[pos]), values[pos]);

				pos++;
				done++;
				return entry;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Iterator<Map.Entry<Short, String>> entryShortIterator() {
		return new Iterator<Map.Entry<Short, String>>() {
			private int pos;
			private int done;

			public boolean hasNext() {
				return done < size;
			}

			public Map.Entry<Short, String> next() {
				if (done >= size)
					throw new NoSuchElementException();

				for (; pos < capacity; pos++) {
					if (values[pos] != null) {
						break;
					}
				}

				Map.Entry<Short, String> entry = new AbstractMap.SimpleEntry<>(keys[pos], values[pos]);

				pos++;
				done++;
				return entry;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Map<String, String> getTagsWithPrefix(String prefix, boolean removePrefix) {
		Map<String, String> map = new HashMap<>();

		int prefixLen = prefix.length();
		for(int i = 0; i < capacity; ++i) {
			if (keys[i] != 0){
				String k = tagDict.get(keys[i]);
				if(k != null && k.startsWith(prefix)) {
				if(removePrefix)
						map.put(k.substring(prefixLen), values[i]);
				else
						map.put(k, values[i]);
				}
			}
		}

		return map;
	}
	

	public String toString() {
		// sort the tags by key to make the result predictable and easier to read
		TreeSet<String> sorted = new TreeSet<>();
		for (int i = 0; i < capacity; i++) {
			if (values[i] != null) {
				sorted.add(tagDict.get(keys[i]) + "=" + values[i]);
			}
		}
		return sorted.toString();
	}
}
