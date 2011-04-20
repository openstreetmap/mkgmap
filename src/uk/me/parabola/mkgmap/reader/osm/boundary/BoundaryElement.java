package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.Polygon;
import java.awt.geom.Area;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;

public class BoundaryElement implements Serializable {
	private final static class CoordValue implements Serializable {
		public final int lat;
		public final int lon;

		public CoordValue(int lat, int lon) {
			super();
			this.lat = lat;
			this.lon = lon;
		}
	}

	private final boolean outer;
	private final List<CoordValue> points;
	private transient Area area;

	public BoundaryElement(boolean outer, List<Coord> points) {
		this.outer = outer;
		this.points = new ArrayList<CoordValue>(points.size());
		for (Coord c : points) {
			this.points.add(new CoordValue(c.getLatitude(), c.getLongitude()));
		}
	}

	public Area getArea() {
		if (area == null) {
			Polygon polygon = new Polygon();
			for (CoordValue co : points) {
				polygon.addPoint(co.lon, co.lat);
			}
			area = new Area(polygon);
		}
		return area;
	}

	public boolean isOuter() {
		return outer;
	}

}
