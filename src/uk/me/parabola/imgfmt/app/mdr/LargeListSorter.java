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
 package uk.me.parabola.imgfmt.app.mdr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * Helper class to perform sort on possibly large lists using sort keys.
 * The list are divided into chunks so that the peak memory usage is reduced.
 * @author Gerd Petermann
 *
 * @param <T>
 */
public abstract class LargeListSorter<T extends NamedRecord> {
	private final Sort sort;
	
	public LargeListSorter(Sort sort) {
		this.sort = sort;
	}

	/**
	 * Sort list in place.
	 * @param list list of records.
	 */
	public void sort(ArrayList<T> list) {
		mergeSort(0, list, 0, list.size());
	}
	
	/**
	 * A merge sort implementation which sorts large chunks using a cache for the keys 
	 * @param depth recursion depth
	 * @param list list to sort
	 * @param start position of first element in list 
	 * @param len number of elements in list 
	 */
	private void mergeSort(int depth, ArrayList<T> list, int start, int len) {
		// we split if the number is very high and recursion is not too deep
		if (len > 1_000_000 && depth < 3) {
			mergeSort(depth+1,list, start, len / 2); // left
			mergeSort(depth+1,list, start + len / 2, len - len / 2); // right
			merge(list,start,len);
		} else {
			// sort one chunk
//			System.out.println("sorting list of roads. positions " + start + " to " + (start + len - 1));
			Map<String, byte[]> cache = new HashMap<>();
			List<SortKey<T>> keys = new ArrayList<>(len);

			for (int i = start; i < start + len; i++) {
				keys.add(makeKey(list.get(i), sort, cache));
			}
			cache = null;
			Collections.sort(keys);
			
			for (int i = 0; i < keys.size(); i++){ 
				SortKey<T> sk = keys.get(i);
				T r = sk.getObject();
				list.set(start+i, r);
			}
			return;
		}
	}
	
	
	private void merge(ArrayList<T> list, int start, int len) {
//		System.out.println("merging positions " + start + " to " + (start + len - 1));
		int pos1 = start;
		int pos2 = start + len / 2;
		int stop1 = start + len / 2;
		int stop2 = start + len;
		boolean fetch1 = true;
		boolean fetch2 = true;
		List<T> merged = new ArrayList<>();
		SortKey<T> sk1 = null;
		SortKey<T> sk2 = null;
		while (pos1 < stop1 &&  pos2 < stop2) {
			if (fetch1 && pos1 < stop1) {
				sk1 = makeKey(list.get(pos1), sort, null);
				fetch1 = false;
			}
			if (fetch2 && pos2 < stop2) {
				sk2 = makeKey(list.get(pos2), sort, null);
				fetch2 = false;
			}
			int d = sk1.compareTo(sk2);
			if (d <= 0) {
				merged.add(sk1.getObject());
				fetch1 = true;
				pos1++;
			} else {
				merged.add(sk2.getObject());
				fetch2 = true;
				pos2++;
			}
		}
		while (pos1 < stop1) {
			merged.add(list.get(pos1++));
		}
		while (pos2 < stop2) {
			merged.add(list.get(pos2++));
		}
		assert merged.size() == len;
		for (int i = 0; i < len; i++) {
			list.set(start+i, merged.get(i));
		}
	}

	protected abstract SortKey<T>  makeKey(T record, Sort sort, Map<String, byte[]> cache);
}
