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

import java.util.List;

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

	/**
	 * Create an area from the given coordinates.  We ensure that no dimention
	 * is zero.
	 *
	 * @param minLat The western latitude.
	 * @param minLong The southern longitude.
	 * @param maxLat The eastern lat.
	 * @param maxLong The northern long.
	 */
	public Area(int minLat, int minLong, int maxLat, int maxLong) {
		this.minLat = minLat;
		if (maxLat == minLat)
			this.maxLat = minLat+1;
		else
			this.maxLat = maxLat;

		this.minLong = minLong;
		if (minLong == maxLong)
			this.maxLong = maxLong+1;
		else
			this.maxLong = maxLong;
	}

	public Area(double minLat, double minLong, double maxLat,
				  double maxLong)
	{
		this(Utils.toMapUnit(minLat), Utils.toMapUnit(minLong)
				, Utils.toMapUnit(maxLat), Utils.toMapUnit(maxLong));
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

	public int getWidth() {
		return maxLong - minLong;
	}

	public int getHeight() {
		return maxLat - minLat;
	}

	public Coord getCenter() {
		return new Coord((minLat + maxLat)/2, (minLong + maxLong)/2);
	}

	public String toString() {
		return "("
				+ Utils.toDegrees(minLat) + ','
				+ Utils.toDegrees(minLong) + ") to ("
				+ Utils.toDegrees(maxLat) + ','
				+ Utils.toDegrees(maxLong) + ')'
				;
	}

	/**
	 * Split this area up into a number of smaller areas.
	 *
	 * @param xsplit The number of pieces to split this area into in the x
	 * direction.
	 * @param ysplit The number of pieces to split this area into in the y
	 * direction.
	 * @return An area containing xsplit*ysplit areas.
	 */
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

		assert areas.length == xsplit * ysplit;
		return areas;
	}

	/**
	 * Get the largest dimention.  So either the width or heigh, depending
	 * on which is larger.
	 *
	 * @return The largest dimention in map units.
	 */
	public int getMaxDimention() {
		return Math.max(getWidth(), getHeight());
	}

	public final boolean contains(Coord co) {
		// return true if co is inside the Area (it may touch the
		// boundary)
		return co.getLatitude() >= minLat
				&& co.getLatitude() <= maxLat
				&& co.getLongitude() >= minLong
				&& co.getLongitude() <= maxLong;
	}

	public final boolean insideBoundary(Coord co) {
		// return true if co is inside the Area and doesn't touch the
		// boundary
		return co.getLatitude() > minLat
				&& co.getLatitude() < maxLat
				&& co.getLongitude() > minLong
				&& co.getLongitude() < maxLong;
	}

	public final boolean onBoundary(Coord co) {
		// return true if co is on the boundary
		return contains(co) && !insideBoundary(co);
	}

	public boolean isEmpty() {
		return minLat >= maxLat || minLong >= maxLong;
	}

	public boolean allInsideBoundary(List<Coord> coords) {
		for (Coord co : coords) {
			if (!insideBoundary(co))
				return false;
		}
		return true;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Area area = (Area) o;

		if (maxLat != area.maxLat) return false;
		if (maxLong != area.maxLong) return false;
		if (minLat != area.minLat) return false;
		if (minLong != area.minLong) return false;

		return true;
	}

	public int hashCode() {
		int result = minLat;
		result = 31 * result + minLong;
		result = 31 * result + maxLat;
		result = 31 * result + maxLong;
		return result;
	}
}
