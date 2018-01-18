/*
 * Copyright (C) 2018.
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
package uk.me.parabola.mkgmap.combiners;

import uk.me.parabola.imgfmt.Sized;

/**
 * Information on subfiles within a .img file.
 *
 * Other kinds of file will just have one of these.
 */
public class SubFileInfo implements Sized{
	private final String name;
	private final long size;

	public SubFileInfo(String name, long size) {
		this.name = name;
		this.size = size;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}
}
