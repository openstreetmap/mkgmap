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
 * Create date: Dec 3, 2007
 */
package uk.me.parabola.mkgmap.filters;

import uk.me.parabola.imgfmt.app.Area;

/**
 * Configuration for filters.  Some filters may need extra information that
 * will be provided here.
 * 
 * @author Steve Ratcliffe
 */
public class FilterConfig {
	private int resolution;
	private Area bounds;

	public int getResolution() {
		return resolution;
	}

	public int getShift() {
		return 24 - getResolution();
	}

	/**
	 * Set the resolution and shift values.
	 *
	 * @param resolution The resolution.
	 */
	public void setResolution(int resolution) {
		this.resolution = resolution;
	}

	public Area getBounds() {
		return bounds;
	}

	public void setBounds(Area bounds) {
		this.bounds = bounds;
	}
}
