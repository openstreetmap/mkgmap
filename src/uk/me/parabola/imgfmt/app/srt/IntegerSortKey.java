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
 * Sort key when you are soring on a simple integer.
 *
 * @author Steve Ratcliffe
 */
public class IntegerSortKey<T> implements SortKey<T> {
	private final T object;
	private final int val;
	private final int second;

	public IntegerSortKey(T object, int val, int second) {
		this.object = object;
		this.val = val;
		this.second = second;
	}

	/**
	 * Get the object associated with this sort key. This will usually be the real object being sorted.
	 */
	public T getObject() {
		return object;
	}

	public int compareTo(SortKey<T> o) {
		IntegerSortKey<T> other = (IntegerSortKey<T>) o;
		if (val == other.val) {
			if (second == other.second)
				return 0;
			else if (second < other.second)
				return -1;
			else
				return 1;
		}
		else if (val < other.val)
			return -1;
		else
			return 1;
	}
}
