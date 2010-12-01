package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.QuadTree;

/**
 * A relation used by the sea generation code.
 * 
 * @author WanMil
 */
public class SeaPolygonRelation extends MultiPolygonRelation {
	private static final Logger log = Logger
			.getLogger(SeaPolygonRelation.class);

	private final QuadTree landCoords;
	private final QuadTree seaCoords;

	private static final int FLOODBLOCKER_POINT_OFFSET = 40;
	private static final double FLOODBLOCKER_RATIO = 0.5;
	private static final int FLOODBLOCKER_MIN_NEGATIVE = 20;

	public SeaPolygonRelation(Relation other, Map<Long, Way> wayMap,
			uk.me.parabola.imgfmt.app.Area bbox) {
		super(other, wayMap, bbox);
		this.landCoords = new QuadTree(bbox);
		this.seaCoords = new QuadTree(bbox);
		// set a special type because this is not the OSM common multipolygon
		// relation
		addTag("type", "mkgmap:seapolygon");
	}

	@Override
	protected void postProcessing() {
		removeFloodedAreas();
		super.postProcessing();
	}

	private void fillQuadTrees() {
		for (Way way : getTileWayMap().values()) {
			boolean layer0 = true;
			try {
				layer0 = way.getTag("layer") == null
						|| Integer.valueOf(way.getTag("layer")) == 0;
			} catch (Exception exp) {
			}

			if (layer0 && way.getTag("highway") != null
					&& "construction".equals(way.getTag("highway")) == false
					&& way.isBoolTag("bridge") == false
					&& way.isBoolTag("tunnel") == false
					&& "dam".equals(way.getTag("waterway")) == false
					&& "pier".equals(way.getTag("man_made")) == false) {
				// save these coords to check if some sea polygons floods the
				// land
				landCoords.addAll(way.getPoints());
			}

			if ("ferry".equals(way.getTag("route"))) {
				// save these coords to check if some sea polygons floods the
				// land
				seaCoords.addAll(way.getPoints());
			} else if ("administrative".equals(way.getTag("boundary"))
					&& way.isBoolTag("maritime")) {
				seaCoords.addAll(way.getPoints());
			}
		}
	}

	private void removeFloodedAreas() {
		fillQuadTrees();

		// create a copy of all resulting ways - the tile way map contains only
		// polygons from
		// the sea generation
		ArrayList<Way> polygons = new ArrayList<Way>(getMpPolygons().values());

		log.info("Starting flood blocker. Polygons to check:", getMpPolygons()
				.size());

//		String baseName = GpxCreator.getGpxBaseName();

		// go through all polygons and check if it contains too many coords of
		// the other type
		for (Way p : polygons) {
			boolean sea = "sea".equals(p.getTag("natural"));

			if (sea) {
				List<Coord> minusCoords = landCoords.get(p.getPoints(), FLOODBLOCKER_POINT_OFFSET);
				List<Coord> positiveCoords = seaCoords.get(p.getPoints());
				log.info("Sea polygon", p.getId(), "contains",
						minusCoords.size(), "land coords and",
						positiveCoords.size(), "sea coords.");
				if (minusCoords.size() - positiveCoords.size() >= FLOODBLOCKER_MIN_NEGATIVE) {
					double area = calcArea(p.getPoints());
					double ratio = ((minusCoords.size() - positiveCoords.size()) * 100000.0d / area);
					log.warn("Flood blocker for sea polygon with center", p
							.getCofG().toOSMURL());
					log.warn("Area: " + area);
					log.warn("Positive: " + positiveCoords.size());
					log.warn("Minus: " + minusCoords.size());
					log.warn("Ratio: " + ratio);
					if (ratio > FLOODBLOCKER_RATIO) {
//						GpxCreator.createGpx(baseName + p.getId() + "_sea_"
//								+ minusCoordsAll.size() + "_" + ratio,
//								p.getPoints(), minusCoordsAll);
//						GpxCreator.createGpx(baseName + p.getId() + "_sea_off_"
//								+ minusCoords.size() + "_" + ratio,
//								p.getPoints(), minusCoords);
						getMpPolygons().remove(p.getId());
					}
				}
			} else {
				List<Coord> vetoCoords = seaCoords.get(p.getPoints(), FLOODBLOCKER_POINT_OFFSET);
				log.info("Land polygon", p.getId(), "contains",
						vetoCoords.size(), "sea coords.");
				if (vetoCoords.isEmpty() == false) {
					log.warn("Flood blocker for land polygon with center", p
							.getCofG().toOSMURL());
					getMpPolygons().remove(p.getId());
				}
			}
		}
		log.info("Flood blocker finished. Resulting polygons:", getMpPolygons()
				.size());
	}

	private double calcArea(List<Coord> polygon) {
		Way w = new Way(0, polygon);
		if (w.clockwise() == false) {
			polygon = new ArrayList<Coord>(polygon);
			Collections.reverse(polygon);
		}
		w = null;
		double area = 0;
		Iterator<Coord> polyIter = polygon.iterator();
		Coord c1 = null;
		Coord c2 = polyIter.next();
		while (polyIter.hasNext()) {
			c1 = c2;
			c2 = polyIter.next();
			area += (double) (c2.getLongitude() + c1.getLongitude())
					* (c1.getLatitude() - c2.getLatitude());
		}
		area = area / 2.0d;
		return area;
	}
}
