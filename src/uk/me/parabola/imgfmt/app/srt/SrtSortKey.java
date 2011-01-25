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
		for (int i = 0; i < this.key.length; i++) {
			int k1 = this.key[i] & 0xff;
			int k2 = ((SrtSortKey) o).key[i] & 0xff;
			if (k1 < k2) {
				return -1;
			} else if (k1 > k2) {
				return 1;
			}
		}

		if (second == ((SrtSortKey) o).second)
			return 0;
		else if (second < ((SrtSortKey) o).second)
			return -1;
		else
			return 1;
	}

	public T getObject() {
		return orig;
	}
}
