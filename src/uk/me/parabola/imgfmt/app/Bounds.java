/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 07-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.Utils;

/**
 * A map area in map units.  There is a constructor available for creating
 * in lat/long form.
 * 
 * @author Steve Ratcliffe
 */
public class Bounds {

	private int minLat;
	private int minLong;
	private int maxLat;
	private int maxLong;

	public Bounds(int minLat, int minLong, int maxLat, int maxLong) {
		this.minLat = minLat;
		this.minLong = minLong;
		this.maxLat = maxLat;
		this.maxLong = maxLong;
	}

	public Bounds(double minLat, double minLong, double maxLat,
				  double maxLong)
	{
		this.minLat = Utils.toMapUnit(minLat);
		this.minLong = Utils.toMapUnit(minLong);
		this.maxLat = Utils.toMapUnit(maxLat);
		this.maxLong = Utils.toMapUnit(maxLong);
	}

	public int getMinLat() {
		return minLat;
	}

	public int getMinLong() {
		return minLong;
	}

	public int getMaxLat() {
		return maxLat;
	}

	public int getMaxLong() {
		return maxLong;
	}

}
