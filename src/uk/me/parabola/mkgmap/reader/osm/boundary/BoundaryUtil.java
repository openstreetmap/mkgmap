package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryUtil {

	public static List<BoundaryElement> splitToElements(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		}

		List<BoundaryElement> bElements = new ArrayList<BoundaryElement>();

		List<List<Coord>> areaElements = Java2DConverter.areaToShapes(area);

		for (List<Coord> singleElement : areaElements) {
			Way w = new Way(0, singleElement);

			boolean outer = w.clockwise();
			bElements.add(new BoundaryElement(outer, singleElement));
		}

		// reverse the list because it starts with the inner elements first and
		// we need the other way round
		Collections.reverse(bElements);

		assert bElements.get(0).isOuter();
		return bElements;
	}

	public static Area convertToArea(List<BoundaryElement> list) {
		Area area = new Area();

		for (BoundaryElement elem : list) {
			if (elem.isOuter()) {
				area.add(elem.getArea());
			} else {
				area.subtract(elem.getArea());
			}
		}
		return area;
	}

}
