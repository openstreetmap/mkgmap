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
 * Author: Steve Ratcliffe
 * Create date: Dec 15, 2007
 */
package uk.me.parabola.imgfmt.app;

/**
 * Represents an item size the position where those items start and the
 * total size of the section.
 */
class Section {
	private char itemSize;
	private int size;
	private int position;

	Section() {
	}

	Section(char itemSize) {
		this.itemSize = itemSize;
	}

	public void inc() {
		size += itemSize;
	}

	public char getItemSize() {
		return itemSize;
	}

	public void setItemSize(char itemSize) {
		this.itemSize = itemSize;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}
}
