/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryElement  {
	private final boolean outer;
	private final List<Coord> points;
	private Area area;

	public BoundaryElement(boolean outer, List<Coord> points) {
		this.outer = outer;
		this.points = new ArrayList<Coord>(points);
	}

	public Area getArea() {
		if (area == null) {
			area = new Area(Java2DConverter.createArea(points));
		}
		return area;
	}

	public boolean isOuter() {
		return outer;
	}

	public List<Coord> getPoints() {
		return points;
	}
	
	public String toString() {
		return isOuter() ? "outer" : "inner";
	}

}
/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryElement  {
	private final boolean outer;
	private final List<Coord> points;
	private Area area;

	public BoundaryElement(boolean outer, List<Coord> points) {
		this.outer = outer;
		this.points = new ArrayList<Coord>(points);
	}

	public Area getArea() {
		if (area == null) {
			area = new Area(Java2DConverter.createArea(points));
		}
		return area;
	}

	public boolean isOuter() {
		return outer;
	}

	public List<Coord> getPoints() {
		return points;
	}
	
	public String toString() {
		return (isOuter() ? "outer" : "inner") + " " + points ;
	}

}
