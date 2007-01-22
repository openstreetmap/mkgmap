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
import uk.me.parabola.log.Logger;

/**
 * A map area in map units.  There is a constructor available for creating
 * in lat/long form.
 * 
 * @author Steve Ratcliffe
 */
public class Area {
	private static final Logger log = Logger.getLogger(Area.class);

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

	/**
	 * Check whether this Bounds is entirely within the given area.
	 * It allows an equal coordinate to be within the area.
	 *
	 * @param area The area to check against.
	 * @return True if this falls entirely within the area.
	 */
	public boolean isWithin(Area area) {
		if (minLat >= area.getMinLat()
				&& maxLat <= area.getMaxLat()
				&& minLong >= area.getMinLong()
				&& maxLong <= area.getMaxLong()) {
			return true;
		} else {
			return false;
		}
	}

	public int getWidth() {
		return maxLong - minLong;
	}

	public int getHeight() {
		return maxLat - minLat;
	}

	public String toString() {
		return "("
				+ minLat + ','
				+ minLong + ") to ("
				+ maxLat + ','
				+ maxLong + ')'
				;
	}

	public Area[] split(int xsplit, int ysplit) {
		Area[] areas =  new Area[xsplit * ysplit];


		int xsize = getWidth() / xsplit;
		int ysize = getHeight() / ysplit;

		int xextra = getWidth() - xsize * xsplit;
		int yextra = getHeight() - ysize * ysplit;
		
		for (int x = 0; x < xsplit; x++) {
			int xstart = minLong + x * xsize;
			int xend = xstart + xsize;
			if (x == xsplit - 1)
				xend += xextra;

			for (int y = 0; y < ysplit; y++) {
				int ystart = minLat + y * ysize;
				int yend = ystart + ysize;
				if (y == ysplit - 1)
					yend += yextra;
				Area a = new Area(ystart, xstart, yend, xend);
				log.debug(x, y, a);
				areas[x * ysplit + y] = a;
			}
		}
		
		return areas;
	}
}
