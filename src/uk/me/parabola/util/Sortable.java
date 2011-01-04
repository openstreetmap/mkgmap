/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Author: Mark Burton
 * Create date: 17-March-2009
 */

package uk.me.parabola.util;

public class Sortable<K extends Comparable<K>, V> implements Comparable<Sortable<K, V>> {
	private final K key;
	private final V value;

	public Sortable(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public K getKey() {
		return key;
	}

	public V getValue() {
		return value;
	}

	public int compareTo(Sortable<K, V> o) {
		int diff = key.compareTo(o.key);
		if(diff == 0 && value instanceof Comparable)
			diff = ((Comparable<V>)value).compareTo(o.value);
		return diff;
	}
}
