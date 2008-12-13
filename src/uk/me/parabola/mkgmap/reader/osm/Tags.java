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

import java.util.Iterator;

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

	private int size;
	private String[] keys;
	private String[] values;

	private int capacity;

	private ExtraEntry extra;

	/**
	 * Used for tags that are added during iteration.
	 */
	static class ExtraEntry {
		private String key;
		private String value;
		private ExtraEntry next;
	}

	//private int hit;
	//private int miss;

	public Tags() {
		keys = new String[INIT_SIZE];
		values = new String[INIT_SIZE];
		capacity = INIT_SIZE;
	}


	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public String get(Object key) {
		Integer ind = keyPos((String) key);
		if (ind == null)
			return null;
		//System.out.printf("hit %d, miss %d\n", hit, miss);
		return values[ind];
	}

	public String put(String key, String value) {
		if (extra != null) {
			extra.key = key;
			extra.value = value;
			extra.next = new ExtraEntry();
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

	private void ensureSpace() {
		while (size + 1 >= capacity) {
			int ncap = capacity*2;
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

		for (int i = k+1; i != k; i++) {
			if (i >= capacity)
				//noinspection AssignmentToForLoopParameter
				i -= capacity;

			String fk = keys[i];
			if (fk == null || fk.equals(key)) {
				//hit++;
				return i;
			}
			//miss++;
		}
		return null;
	}

	/**
	 * Iterates over the tags in a special way that is used to look up in
	 * the rules.
	 *
	 * If you have the tags a=b, c=d then you will get the following strings
	 * returned: "a=b", "a=*", "c=d", "c=*".
	 */
	public Iterator<String> iterator() {
		return new Iterator<String>() {
			private int pos;
			private String wild;
			private boolean doWild;

			// Set the extra field in the containing class.
			{ extra = new ExtraEntry(); }

			public boolean hasNext() {
				if (doWild)
					return true;
				for (int i = pos; i < capacity; i++) {
					if (values[i] != null) {
						pos = i;
						return true;
					}
				}
				if (extra != null && extra.value != null)
					return true;
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
				if (extra != null && extra.value != null) {
					ExtraEntry ex = extra;

					extra = null;
					put(ex.key, ex.value);

					extra = ex.next;
					doWild = true;
					wild = ex.value;
					return ex.key + '=' + ex.value;
				}
				return null;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
