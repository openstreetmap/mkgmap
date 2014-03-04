/*
 * Copyright (C) 2010.
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

import java.util.Arrays;

/**
 * Sort key created from a Srt {@link Sort} object that allows strings to be compared according to that sorting
 * scheme.
 *
 * @author Steve Ratcliffe
 */
class SrtSortKey<T> implements SortKey<T> {
	private final T orig;
	private final byte[] key;
	private int second;

	public SrtSortKey(T orig, byte[] key) {
		this.orig = orig;
		this.key = key;
	}

	public SrtSortKey(T orig, byte[] key, int second) {
		this.orig = orig;
		this.key = key;
		this.second = second;
	}

	public int compareTo(SortKey<T> o) {
		SrtSortKey<T> other = (SrtSortKey<T>) o;
		int length = Math.min(this.key.length, other.key.length);
		for (int i = 0; i < length; i++) {
			int k1 = this.key[i] & 0xff;
			int k2 = other.key[i] & 0xff;
			if (k1 < k2) {
				return -1;
			} else if (k1 > k2) {
				return 1;
			}
		}

		//if (this.key.length < other.key.length)
		//	return -1;
		//else if (this.key.length > other.key.length)
		//	return 1;

		if (second == other.second)
			return 0;
		else if (second < other.second)
			return -1;
		else
			return 1;
	}

	public T getObject() {
		return orig;
	}

	public String toString() {
		return String.format("%s,%d", Arrays.toString(key), second);
	}
}
