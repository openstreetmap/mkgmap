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
import java.util.Map.Entry;

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
		assert value != null : "value is null";
		ensureSpace();
		short dictIdx = tagDict.xlate(key);
		int ind = keyPos(dictIdx);
		if (ind < 0)
			assert false : "keyPos(" + key + ") returns null - size = " + keySize + ", capacity = " + capacity;
		keys[ind] = dictIdx;

		String old = values[ind];
		if (old == null) {
			keySize++;
			size++;
		}
		values[ind] = value;;

		return old;
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
		short kd = tagDict.xlate((String) key);
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

			public boolean hasNext() {
				// After every normal entry there is a wild card entry.
				//if (doWild)
				//	return true;

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
				/*if (doWild) {
					doWild = false;
					return wild + "=*";
				} else*/ if (pos < capacity) {
					for (int i = pos; i < capacity; i++) {
						if (values[i] != null) {
							pos = i+1;
							return (tagDict.get(keys[i]) + "=" + values[i]);
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
				Map.Entry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(tagDict.get(keys[pos]), values[pos]);

				pos++;
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
			
			public boolean hasNext() {
				for (int i = pos; i < capacity; i++) {
					if (values[i] != null) {
						pos = i;
						return true;
					}
				}
				return false;
			}

			public Map.Entry<Short, String> next() {
				Map.Entry<Short, String> entry = new AbstractMap.SimpleEntry<Short, String>(keys[pos], values[pos]);

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
	
	public void removeAll() {
		Arrays.fill(keys, TagDict.INVALID_TAG_VALUE);
		Arrays.fill(values, null);
		keySize = 0;
		size = 0;
	}
	
	public String toString() {
		StringBuilder s =new StringBuilder();
		s.append("[");
		Iterator<Entry<String,String>> tagIter = entryIterator();
		while (tagIter.hasNext()) {
			Entry<String,String> tag = tagIter.next();
			if (s.length() > 1) {
				s.append("; ");
			}
			s.append(tag.getKey());
			s.append("=");
			s.append(tag.getValue());
		}
		s.append("]");
		return s.toString();
	}
	
	/**
	 * Each tag has a position in the TagDict. This routine fills an array
	 * so that the caller can use direct access. 
	 * The caller has to make sure that the array is large enough to hold
	 * the values he is looking for.  
	 * @param tagVals
	 * @return
	 */
	public int expand(short[] keyArray, String[] tagVals){
		if (tagVals == null)
			return 0;
		int maxKey = tagVals.length - 1;
		int cntTags = 0;
		for (int i = 0; i< capacity; i++){
			short tagKey = keys[i];
			if (tagKey != TagDict.INVALID_TAG_VALUE && values[i] != null && tagKey <= maxKey){
				tagVals[tagKey] = values[i];
				keyArray[cntTags++] = tagKey;
			}

		}
		return cntTags;
	}
}
