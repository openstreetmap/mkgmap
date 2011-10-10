/**
 * Copyright (C) 2006 Steve Ratcliffe
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Author: steve
 * Date: 23-Dec-2006
 */
package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * A shape or polygon is just the same as a line really as far as I can tell.
 * There are some things that you cannot do with them semantically.
 *
 * @author Steve Ratcliffe.
 */
public class MapShape extends MapLine {// So top code can link objects from here

	public MapShape() {
	}

	MapShape(MapShape s) {
		super(s);
	}

	public MapShape copy() {
		return new MapShape(this);
	}

	public void setDirection(boolean direction) {
		throw new IllegalArgumentException(
				"can't set a direction on a polygon");
	}
	
	/**
	 * Checks if a point is contained within this shape. Points on the
	 * edge of the shape are considered inside.
	 * 
	 * @param co point to check
	 * @return true if point is in shape, false otherwise
	 */
	public boolean contains(Coord co) {
		return contains(this.getPoints(), co, true);
	}
	
	/**
	 * Checks if a point is contained within a shape.
	 * 
	 * @param points points that define the shape
	 * @param target point to check
	 * @param onLineIsInside if a point on the line should be considered inside the shape
	 * @return true if point is contained within the shape, false if the target point is outside the shape
	 */
	private static boolean contains(List<Coord> points, Coord target, boolean onLineIsInside) {
		// implementation of the Ray casting algorithm as described here:
		// http://en.wikipedia.org/wiki/Point_in_polygon
		// with inspiration from:
		// http://www.visibone.com/inpoly/
		if (points.size() < 3)
			return false;

		// complete the shape if we're dealing with a MapShape that is not closed
		Coord start = points.get(0);
		Coord end = points.get(points.size() - 1);
		if (!start.equals(end)) {
			// make copy of the shape's geometry 
			List<Coord> pointsTemp = new ArrayList<Coord>(points.size() + 1);
			for (Coord coord : points) {
				pointsTemp.add(new Coord(coord.getLatitude(), coord.getLongitude()));
			}
			pointsTemp.add(new Coord(start.getLatitude(), start.getLongitude()));
			points = pointsTemp;
		}

		int xtarget = target.getLatitude();
		int ytarget = target.getLongitude();

		boolean inside = false;for (int i = 0; i < points.size() - 1; i++) {

			// apply transformation points to change target point to (0,0)
			int x0 = points.get(i).getLatitude() - xtarget;
			int y0 = points.get(i).getLongitude() - ytarget;
			int x1 = points.get(i+1).getLatitude() - xtarget;
			int y1 = points.get(i+1).getLongitude() - ytarget;

			// ensure that x0 is smaller than x1 so that we can just check to see if the line intersects the y axis easily
			if (x0 > x1) {
				int xtemp = x0;
				int ytemp = y0;
				x0 = x1;
				y0 = y1;
				x1 = xtemp;
				y1 = ytemp;
			}

			// use (0,0) as target because points already transformed
			if (isPointOnLine(x0, y0, x1, y1, 0, 0))
				return onLineIsInside;

			// explanation of if statement 
			//
			// (x0 < 0 && x1 >= 0):
			// are the x values between the y axis? only include points from the right
			// with this check so that corners aren't counted twice 
			// 
			// (y0 * (x1 - x0) > (y1 - y0) * x0):
			// from y  = mx + b: 
			//   => b = y0 ((y1 - y0) / (x1 - x0)) * x0
			// for intersection,    b > 0
			// from y = mx + b,     b = y - mx
			//                  =>  y - mx > 0
			//                  => y0 - ((y1 - y0) / (x1 - x0)) * x0 > 0
			//                  => y0 > ((y1 - y0) / (x1 - x0)) * x0
			// from 'if (x0 > x1)',  x1 >= x0
			//                  => x1 - x0 >=0
			//                  => y0 * (x1 - x0) > (y1 - y0) * x0
			if ((x0 < 0 && x1 >= 0) && (y0 * (x1 - x0)) > ((y1 - y0) * x0))
				inside = !inside;
		}

		return inside;
	}

	/**
	 * Checks if a point is on a line.
	 * 
	 * @param x0 x value of first point in line
	 * @param y0 y value of first point in line
	 * @param x1 x value of second point in line
	 * @param y1 y value of second point in line
	 * @param xt x value of target point
	 * @param yt y value of target point
	 * @return return true if point is on the line, false if the point isn't on the line
	 */
	private static boolean isPointOnLine(int x0, int y0, int x1, int y1, int xt, int yt) {
		// this implementation avoids using doubles
		// apply transformation points to change target point to (0,0)
		x0 -= xt;
		y0 -= yt;
		x1 -= xt;
		y1 -= yt;

		// ensure that x0 is smaller than x1 so that we can just check to see if the line intersects the y axis easily
		if (x0 > x1) {
			int xtemp = x0;
			int ytemp = y0;
			x0 = x1;
			y0 = y1;
			x1 = xtemp;
			y1 = ytemp;
		}

		// if a point is on the edge of shape (on a line), it's considered outside the shape
		// special case if line is on y-axis
		if (x0 == 0 && x1 == 0) {
			// ensure that y0 is smaller than y1 so that we can just check if the line intersects the x axis
			if (y0 > y1) {
				int ytemp = y0;
				y0 = y1;
				y1 = ytemp;
			}
			// test to see if we have a vertical line touches x-axis
			if (y0 <= 0 && y1 >= 0)
				return true;
			// checks if point is on the line, see comments in contain() for derivation of similar 
			// formula - left as an exercise to the reader ;)
		} else if ((x0 <= 0 && x1 >= 0) && (y0 * (x1 - x0)) == ((y1 - y0) * x0)) {
			return true;
		}
		return false;
	}
					
					
}
