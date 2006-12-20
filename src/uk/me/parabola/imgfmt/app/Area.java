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
public class Area {

	private final int minLat;
	private final int minLong;
	private final int maxLat;
	private final int maxLong;

	public Area(int minLat, int minLong, int maxLat, int maxLong) {
		this.minLat = minLat;
		this.minLong = minLong;
		this.maxLat = maxLat;
		this.maxLong = maxLong;
	}

	public Area(double minLat, double minLong, double maxLat,
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

// --Commented out by Inspection START (19/12/06 16:31):
//	/**
//	 * Check whether this Bounds is entirely within the given area.
//	 * It allows an equal coordinate to be within the area.
//	 *
//	 * @param area The area to check against.
//	 * @return True if this falls entirely within the area.
//	 */
//	public boolean isWithin(Area area) {
//		if (minLat >= area.getMinLat()
//				&& maxLat <= area.getMaxLat()
//				&& minLong >= area.getMinLong()
//				&& maxLong <= area.getMaxLong()) {
//			return true;
//		} else {
//			return false;
//		}
//	}
// --Commented out by Inspection STOP (19/12/06 16:31)

	public String toString() {
		return "("
				+ minLat + ','
				+ minLong + ") to ("
				+ maxLat + ','
				+ maxLong + ')'
				;
	}
}
