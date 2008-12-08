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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A map implementation that allows values to be added during an iteration
 * and for those values be guaranteed to show up later in the iteration.
 *
 * It also uses less memory (hash maps are the main use of memory in the
 * application).  Performance is unchanged compared with a regular HashMap.
 * 
 * @author Steve Ratcliffe
 */
public class SimpleMap implements Map<String, String> {
	private static final int INIT_SIZE = 8;

	private int size;
	private String[] keys;
	private String[] values;

	private int capacity;

	private ExtraEntry extra;

	static class ExtraEntry {
		private String key;
		private String value;
		private ExtraEntry next;
	}

	//private int hit;
	//private int miss;

	public SimpleMap() {
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

	public boolean containsKey(Object key) {
		throw new UnsupportedOperationException();
	}

	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
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

	public String remove(Object key) {
		Integer k = keyPos((String) key);

		if (k != null) {
			String old = values[k];
			values[k] = null;
			if (old != null)
				size--;
			return old;
		}
		return null;
	}

	public void putAll(Map<? extends String, ? extends String> t) {
		throw new UnsupportedOperationException();
	}

	public void clear() {
		throw new UnsupportedOperationException();
	}

	public Set<String> keySet() {
		throw new UnsupportedOperationException();
	}

	public Collection<String> values() {
		throw new UnsupportedOperationException();
	}

	public Set<Entry<String, String>> entrySet() {
		throw new UnsupportedOperationException();
	}

	public Iterator<String> iterator() {
		return new Iterator<String>() {
			private int pos;
			private String wild;
			private boolean doWild;

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
