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
 * A sort key that allows efficient comparison of a string with a particular sorting order multiple times.
 *
 * The general idea is that you create a key for each object to be sorted and then sort the keys. Once the
 * keys are sorted you retrieve the original object via the {@link #getObject} method.
 * 
 * @author Steve Ratcliffe
 */
public interface SortKey<T> extends Comparable<SortKey<T>> {
	/**
	 * Get the object associated with this sort key.
	 * This will usually be the real object being sorted.
	 */
	public T getObject();
}
