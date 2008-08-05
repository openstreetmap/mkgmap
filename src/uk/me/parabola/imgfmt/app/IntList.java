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
 * Create date: 20-Jul-2008
 */
package uk.me.parabola.imgfmt.app;

/**
 * A list of ints based on the primative 'int' type.
 * @author Steve Ratcliffe
 */
public class IntList {
	private static final int INIT_SIZE = 10;
	private static final int CAPACITY_INC = 20;

	private int[] list;
	private int size;
	private int capacity;

	public IntList() {
		this(INIT_SIZE);
	}

	public IntList(int initsize) {
		list = new int[initsize];
		capacity = initsize;
		size = 0;
	}

	public void add(int val) {
		checkCapacity();
		list[size++] = val;
	}

	public int get(int n) {
		return list[n];
	}

	private void checkCapacity() {
		if (size == capacity) {
			int[] newlist = new int[capacity + CAPACITY_INC];
			System.arraycopy(list, 0, newlist, 0, list.length);
			capacity = newlist.length;
			list = newlist;
		}
	}
}
