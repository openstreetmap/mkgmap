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
