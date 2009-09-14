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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Store the tags that belong to an Element.
 *
 * Used to use a HashMap for this.  We have a requirement to be able
 * to add to the map during iteration over it so this class was written
 * instead.
 *
 * It should also uses less memory (hash maps are the main use of memory in the
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

	// This is a flag that iteration is taking place and a place to store
	// tags that are added during iteration.
	private ExtraEntry extra;

	/**
	 * Used for tags that are added during iteration.
	 */
	static class ExtraEntry {
		private String key;
		private String value;
		private ExtraEntry next;
	}

	public Tags() {
		keys = new String[INIT_SIZE];
		values = new String[INIT_SIZE];
		capacity = INIT_SIZE;
	}

	public String get(Object key) {
		// If we are iterating over this tag set then we may have extra
		// entries.  These have to be searched first.
		if (extra != null) {
			for (ExtraEntry ent = extra; ent.next != null; ent = ent.next)
					if (ent.key.equals(key))
						return ent.value;
		}

		Integer ind = keyPos((String) key);
		if (ind == null)
			return null;

		return values[ind];
	}

	public String put(String key, String value) {
		if (extra != null) {
			// Deal with the case where we are adding a tag during iteration
			// of the tags.  This is flagged by extra being non null.
			ExtraEntry emptyEntry = extra;
			while (emptyEntry.next != null && !key.equals(emptyEntry.key))
				emptyEntry = emptyEntry.next;
			
			emptyEntry.key = key;
			emptyEntry.value = value;
			emptyEntry.next = new ExtraEntry();
			return null;
		}

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
		addExtraItems();
		
		Tags cp = new Tags();
		cp.size = size;
		cp.capacity = capacity;

		// Copy the arrays.  (For java 1.6 we can use Arrays.copyOf().)
		cp.keys = new String[keys.length];
		System.arraycopy(keys, 0, cp.keys, 0, keys.length);
		cp.values = new String[values.length];
		System.arraycopy(values, 0, cp.values, 0, values.length);
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
			private ExtraEntry nextEntry;

			// Set the extra field in the containing class.
			{
				addExtraItems();
				extra = new ExtraEntry();
				nextEntry = extra;
			}

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

				// Entries that were added during iteration
				if (nextEntry != null && nextEntry.value != null)
					return true;

				// Add everything from extra and clean up, there is no guarantee
				// that this gets called here (because you may stop the
				// iteration early) but in the normal case it will be called
				// and will remove all the ExtraEntry objects, thus keeping
				// the memory usage to a minimum.
				addExtraItems();
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

				ExtraEntry ex = nextEntry;
				if (nextEntry != null && nextEntry.value != null) {
					nextEntry = ex.next;
					doWild = true;
					wild = ex.key;
					return ex.key + '=' + ex.value;
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
				Map.Entry<String, String> entry = new StringEntry(keys[pos], values[pos]);

				pos++;
				return entry;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	// TODO replace with AbstractMap.SimpleEntry as soon as we go to 1.6
	static class StringEntry implements Map.Entry<String,String> {
		String key;
		String value;

		public StringEntry(String key, String value) {
			this.key   = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public String setValue(String value) {
			String oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		public boolean equals(Object o) {
			if (!(o instanceof Map.Entry))
				return false;
			Map.Entry<String,String> e = (Map.Entry)o;
			return eq(key, e.getKey()) && eq(value, e.getValue());
		}

		public int hashCode() {
			return ((key   == null)? 0: key.hashCode()) ^
					((value == null)? 0: value.hashCode());
		}

		public String toString() {
			return key + "=" + value;
		}

		private static boolean eq(Object o1, Object o2) {
			return (o1 == null ? o2 == null : o1.equals(o2));
		}
	}

	/**
	 * Add the items that are in 'extra' to the map proper.
	 */
	private void addExtraItems() {
		if (extra != null) {
			ExtraEntry e = extra;
			extra = null;
			for (; e != null; e = e.next)
				if (e.value != null)
					put(e.key, e.value);
		}
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
