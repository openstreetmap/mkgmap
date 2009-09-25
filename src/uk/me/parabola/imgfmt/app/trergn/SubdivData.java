/*
 * Copyright (C) 2009.
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

package uk.me.parabola.imgfmt.app.trergn;

/**
 * Used to initialise a subdivision when we are reading it from a file,
 * rather than creating it out of thin air.
 */
public class SubdivData {
	private final int flags;
	private final int lat;
	private final int lon;
	private final int width;
	private final int height;
	private final int rgnPointer;

	public SubdivData(int flags, int lat, int lon, int width, int height, int rgnPointer) {
		this.flags = flags;
		this.lat = lat;
		this.lon = lon;
		this.width = width;
		this.height = height;
		this.rgnPointer = rgnPointer;
	}

	public int getFlags() {
		return flags;
	}

	public int getLat() {
		return lat;
	}

	public int getLon() {
		return lon;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getRgnPointer() {
		return rgnPointer;
	}
}
