/*
 * Copyright (C) 2011.
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

package uk.me.parabola.imgfmt.app.srt;

/**
 * Combines a number of sort keys into one. The first is the primary sort and contains the
 * actual object being sorted.
 * 
 * @author Steve Ratcliffe
 */
public class MultiSortKey<T> implements SortKey<T> {
	private final SortKey<T> key1;
	private final SortKey<T> key2;
	private final SortKey<T> key3;

	public MultiSortKey(SortKey<T> key1, SortKey<T> key2, SortKey<T> key3) {
		this.key1 = key1;
		this.key2 = key2;
		this.key3 = key3;
	}

	public T getObject() {
		T first = key1.getObject();
		if (first == null)
			return key2.getObject();
		else
			return first;
	}

	public int compareTo(SortKey<T> o) {
		MultiSortKey<T> other = (MultiSortKey<T>) o;
		int res = key1.compareTo(other.key1);
		if (res == 0) {
			res = key2.compareTo(other.key2);
			if (res == 0 && key3 != null) {
				res = key3.compareTo(other.key3);
			}
		}
		return res;
	}
}
