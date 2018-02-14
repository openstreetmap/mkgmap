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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import uk.me.parabola.imgfmt.MapFailedException;
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
	public final static Area PLANET = new Area(-90.0, -180.0, 90.0, 180.0);

	private final int minLat;
	private final int minLong;
	private final int maxLat;
	private final int maxLong;

	/**
	 * Create an area from the given coordinates.  We ensure that no dimension
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
		return new Coord((minLat + maxLat)/2, (minLong + maxLong)/2);// high prec not needed
	}

	public String debugString() {
		return String.format(Locale.ROOT, "(%d, %d) to (%d, %d) (%.6f, %.6f) to (%.6f, %.6f)", 
				minLat, minLong,
				maxLat, maxLong,
				Utils.toDegrees(minLat), Utils.toDegrees(minLong),
				Utils.toDegrees(maxLat), Utils.toDegrees(maxLong)); 	
	}
	
	public String toString() {
		return "("
				+ Utils.toDegrees(minLat) + ','
				+ Utils.toDegrees(minLong) + ") to ("
				+ Utils.toDegrees(maxLat) + ','
				+ Utils.toDegrees(maxLong) + ')'
				;
	}

	public String toHexString() {
		return "(0x"
				+ Integer.toHexString(minLat) + ",0x"
				+ Integer.toHexString(minLong) + ") to (0x"
				+ Integer.toHexString(maxLat) + ",0x"
				+ Integer.toHexString(maxLong) + ')'; 
	}

	/**
	 * Round integer to nearest power of 2.
	 *
	 * @param val The number of be rounded.
	 * @param shift The power of 2.
	 * @return The rounded number (binary half rounds up).
	 */
	private static int roundPof2(int val, int shift) {
		if (shift <= 0)
			return val;
		return (((val >> (shift-1)) + 1) >> 1) << shift;
	}

	/**
	 * Split this area up into a number of smaller areas.
	 *
	 * @param xsplit The number of pieces to split this area into in the x
	 * direction.
	 * @param ysplit The number of pieces to split this area into in the y
	 * direction.
	 * @param resolutionShift round to this power of 2.
	 * @return An array containing xsplit*ysplit areas or null if can't split in half.
	 * @throws MapFailedException if more complex split operation couldn't be honoured.
	 */
	public Area[] split(int xsplit, int ysplit, int resolutionShift) {
		Area[] areas =  new Area[xsplit * ysplit];

		int xstart;
		int xend;
		int ystart;
		int yend;
		int nAreas = 0;

		xstart = minLong;
		for (int x = 0; x < xsplit; x++) {
			if (x == xsplit - 1)
				xend = maxLong;
			else
				xend = roundPof2(xstart + (maxLong - xstart) / (xsplit - x),
						 resolutionShift);
			ystart = minLat;
			for (int y = 0; y < ysplit; y++) {
				if (y == ysplit - 1)
					yend = maxLat;
				else
					yend = roundPof2(ystart + (maxLat - ystart) / (ysplit - y),
							 resolutionShift);
				if (xstart < xend && ystart < yend) {
					Area a = new Area(ystart, xstart, yend, xend);
//					log.debug(x, y, a);
					log.debug("Area.split", minLat, minLong, maxLat, maxLong, "res", resolutionShift, "to", ystart, xstart, yend, xend);
					areas[nAreas++] = a;
				} else
					log.warn("Area.split", minLat, minLong, maxLat, maxLong, "res", resolutionShift, "can't", xsplit, ysplit);
				ystart = yend;
			}
			xstart = xend;
		}

		if (nAreas == areas.length) // no problem
			return areas;
// beware - MapSplitter.splitMaxSize requests split of 1/1 if the original area wasn't too big
		else if (nAreas == 1) // failed to split in half
			return null;
		else if (areas.length == 1 && areas[0] == null)
			return null;
		else
			throw new MapFailedException("Area split shift align problems");
	}

	/**
	 * Get the largest dimension.  So either the width or height, depending
	 * on which is larger.
	 *
	 * @return The largest dimension in map units.
	 */
	public int getMaxDimension() {
		return Math.max(getWidth(), getHeight());
	}

	/**
	 * 
	 * @param co a coord
	 * @return true if co is inside the Area (it may touch the boundary)
	 */
	public final boolean contains(Coord co) {
		int latHp = co.getHighPrecLat();
		int lonHp = co.getHighPrecLon();
		return latHp  >= (minLat << Coord.DELTA_SHIFT)
				&& latHp <= (maxLat << Coord.DELTA_SHIFT)
				&& lonHp >= (minLong << Coord.DELTA_SHIFT)
				&& lonHp <= (maxLong << Coord.DELTA_SHIFT);
	}

	/**
	 * 
	 * @param other an area
	 * @return true if the other area is inside the Area (it may touch the boundary)
	 */
	public final boolean contains(Area other) {
		return other.getMinLat() >= minLat
				&& other.getMaxLat() <= maxLat
				&& other.getMinLong() >= minLong
				&& other.getMaxLong() <= maxLong;
	}

	/**
	 * @param co a coord
	 * @return true if co is inside the Area and doesn't touch the boundary
	 */
	public final boolean insideBoundary(Coord co) {
		int latHp = co.getHighPrecLat();
		int lonHp = co.getHighPrecLon();
		
		return latHp  > (minLat << Coord.DELTA_SHIFT)
				&& latHp < (maxLat << Coord.DELTA_SHIFT)
				&& lonHp > (minLong << Coord.DELTA_SHIFT)
				&& lonHp < (maxLong << Coord.DELTA_SHIFT);
	}
	

	
	/**
	 * 
	 * @param other an area
	 * @return true if the other area is inside the Area and doesn't touch the boundary 
	 */
	public final boolean insideBoundary(Area other) {
		return other.getMinLat() > minLat
				&& other.getMaxLat() < maxLat
				&& other.getMinLong() > minLong
				&& other.getMaxLong() < maxLong;
	}


	/**
	 * @param co
	 * @return true if co is on the boundary
	 */
	public final boolean onBoundary(Coord co) {
		return contains(co) && !insideBoundary(co);
	}
	
	/**
	 * Checks if this area intersects the given bounding box at least
	 * in one point.
	 * 
	 * @param bbox an area
	 * @return <code>true</code> if this area intersects the bbox; 
	 * 		   <code>false</code> else
	 */
	public final boolean intersects(Area bbox) {
		return minLat <= bbox.getMaxLat() && maxLat >= bbox.getMinLat() && 
			minLong <= bbox.getMaxLong() && maxLong >= bbox.getMinLong();
	}

	public boolean isEmpty() {
		return minLat >= maxLat || minLong >= maxLong;
	}

	/**
	 * 	
	 * @param coords a list of coord instances
	 * @return false if any of the coords lies on or outside of this area
	 */
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
	
	/**
	 * @return list of coords that form the rectangle
	 */
	public List<Coord> toCoords(){
		List<Coord> coords = new ArrayList<Coord>(5);
		Coord start = new Coord(minLat, minLong);
		coords.add(start);
		Coord co = new Coord(minLat, maxLong);
		coords.add(co);
		co = new Coord(maxLat, maxLong);
		coords.add(co);
		co = new Coord(maxLat, minLong);
		coords.add(co);
		coords.add(start);
		return coords;
	}

	public Area intersect(Area other) {
		return new Area(Math.max(minLat, other.minLat), Math.max(minLong, other.minLong),
				Math.min(maxLat, other.maxLat), Math.min(maxLong, other.maxLong));
	}
}
