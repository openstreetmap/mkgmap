/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * 
 * Author: Steve Ratcliffe
 * Create date: 08-Dec-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
public class Tags implements Iterable<String> {
	private static final int INIT_SIZE = 8;

	private short size;
	private short capacity;

	private String[] keys;
	private String[] values;

	public Tags() {
		keys = new String[INIT_SIZE];
		values = new String[INIT_SIZE];
		capacity = INIT_SIZE;
	}

	public String get(Object key) {
		Integer ind = keyPos((String) key);
		if (ind == null)
			return null;

		return values[ind];
	}

	public String put(String key, String value) {
		ensureSpace();
		Integer ind = keyPos(key);
		if (ind == null)
			assert false;
		keys[ind] = key;

		String old = values[ind];
		if (old == null)
			size++;
		values[ind] = value;

		return old;
	}

	public String remove(Object key) {
		Integer k = keyPos((String) key);

		if (k != null) {
			// because of the way this works, you can never remove keys
			// except when resizing.
			String old = values[k];
			values[k] = null;
			if (old != null)
				size--;
			return old;
		}
		return null;
	}
	
	/**
	 * Make a deep copy of this object.
	 * @return A copy of this object.
	 */
	Tags copy() {
		Tags cp = new Tags();
		cp.size = size;
		cp.capacity = capacity;

		cp.keys = Arrays.copyOf(keys, keys.length);
		cp.values = Arrays.copyOf(values, values.length);
		return cp;
	}

	private void ensureSpace() {
		while (size + 1 >= capacity) {
			short ncap = (short) (capacity*2);
			String[] okey = keys;
			String[] oval = values;
			keys = new String[ncap];
			values = new String[ncap];
			capacity = ncap;
			for (int i = 0; i < okey.length; i++) {
				String k = okey[i];
				if (k != null)
					put(k, oval[i]);
			}
		}
		assert size < capacity;
	}

	private Integer keyPos(String key) {
		int h = key.hashCode();
		int k = h & (capacity - 1);

		int i = k;
		do {
			String fk = keys[i];
			if (fk == null || fk.equals(key))
				return i;
			i++;
			if (i >= capacity)
				i = 0;
		} while (i != k);
		return null;
	}

	/**
	 * Iterates over the tags in a special way that is used to look up in
	 * the rules.
	 *
	 * If you have the tags a=b, c=d then you will get the following strings
	 * returned: "a=b", "a=*", "c=d", "c=*".
	 *
	 * If you add a tag during the iteration, then it is guaranteed to
	 * appear later in the iteration.
	 */
	public Iterator<String> iterator() {
		return new Iterator<String>() {
			private int pos;
			private String wild;
			private boolean doWild;

			public boolean hasNext() {
				// After every normal entry there is a wild card entry.
				if (doWild)
					return true;

				// Normal entries in the map
				for (int i = pos; i < capacity; i++) {
					if (values[i] != null) {
						pos = i;
						return true;
					}
				}

				return false;
			}

			/**
			 * Get the next tag as a single string.  Also returns wild card
			 * entries.
			 */
			public String next() {
				if (doWild) {
					doWild = false;
					return wild + "=*";
				} else if (pos < capacity) {
					for (int i = pos; i < capacity; i++) {
						if (values[i] != null) {
							doWild = true;
							wild = keys[i];
							pos = i+1;
							return (keys[i] + "=" + values[i]);
						}
					}
					pos = capacity;
				}

				return null;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Iterator<Map.Entry<String, String>> entryIterator() {
		return new Iterator<Map.Entry<String, String>>() {
			private int pos;
			
			public boolean hasNext() {
				for (int i = pos; i < capacity; i++) {
					if (values[i] != null) {
						pos = i;
						return true;
					}
				}
				return false;
			}

			public Map.Entry<String, String> next() {
				Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(keys[pos], values[pos]);

				pos++;
				return entry;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public Map<String, String> getTagsWithPrefix(String prefix, boolean removePrefix) {
		Map<String, String> map = new HashMap<String, String>();

		int prefixLen = prefix.length();
		for(int i = 0; i < capacity; ++i) {
			if(keys[i] != null && keys[i].startsWith(prefix)) {
				if(removePrefix)
					map.put(keys[i].substring(prefixLen), values[i]);
				else
					map.put(keys[i], values[i]);
			}
		}

		return map;
	}
	
	public void removeAll() {
		for (int i = 0; i < capacity; i++){
			keys[i] = null;
			values[i] = null;
		}
		size = 0;
	}
}
