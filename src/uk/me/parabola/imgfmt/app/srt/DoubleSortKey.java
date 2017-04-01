/*
 * Copyright (C) 2017.
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
 * Combines a pair of sort keys into one. The first is the primary sort and contains the
 * actual object being sorted.
 * 
 * @author Steve Ratcliffe
 * @author Gerd Petermann
 */
public class DoubleSortKey<T> implements SortKey<T> {
	private final SortKey<T> key1;
	private final SortKey<T> key2;

	public DoubleSortKey(SortKey<T> key1, SortKey<T> key2) {
		this.key1 = key1;
		this.key2 = key2;
	}

	public T getObject() {
		return key1.getObject();
	}

	public int compareTo(SortKey<T> o) {
		DoubleSortKey<T> other = (DoubleSortKey<T>) o;
		int res = key1.compareTo(other.key1);
		if (res == 0) {
			res = key2.compareTo(other.key2);
		}
		return res;
	}
}
