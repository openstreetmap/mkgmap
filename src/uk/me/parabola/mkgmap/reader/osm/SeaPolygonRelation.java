package uk.me.parabola.mkgmap.reader.osm;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.GpxCreator;
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

	private boolean floodBlocker = true;
	private int floodBlockerGap = 40;
	private double floodBlockerRatio = 0.5d;
	private int floodBlockerThreshold = 20;
	private boolean debug = false;
	private DecimalFormat format = new DecimalFormat("0.0000");
	private Rule floodBlockerRules;
	
	private String[] landTag = new String[] {"natural","land"};

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
		if (isFloodBlocker()) {
			removeFloodedAreas();
		}
		super.postProcessing();
	}

	private void fillQuadTrees() {
		final AtomicBoolean isLand = new AtomicBoolean(false);
		final AtomicBoolean isSea = new AtomicBoolean(false);
		TypeResult fakedType = new TypeResult() {
			@Override
			public void add(Element el, GType type) {
				if (log.isDebugEnabled())
					log.debug(el.getId(),type);
				if (type.getType() == 0x01) {
					isLand.set(true);
				} else if (type.getType() == 0x02) {
					isSea.set(true);
				}
			}
		};
		for (Way way : getTileWayMap().values()) {
			if (log.isDebugEnabled())
				log.debug("Check usage of way for floodblocker:", way.getId(), way.toTagString());
			floodBlockerRules.resolveType(way, fakedType);

			if (isLand.get()) {
				// save these coords to check if some sea polygons floods
				// the land
				log.debug("Way", way.getId(), "identified as land");
				landCoords.addAll(way.getPoints());
				isLand.set(false);

			} else if (isSea.get()) {
				// save these coords to check if some sea polygons floods the
				// land
				log.debug("Way", way.getId(), "identified as sea");
				seaCoords.addAll(way.getPoints());
				isSea.set(false);
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

		String baseName = GpxCreator.getGpxBaseName();
		if (debug) {
			GpxCreator.createAreaGpx(baseName + "bbox", getBbox());
		}

		// go through all polygons and check if it contains too many coords of
		// the other type
		for (Way p : polygons) {
			boolean sea = "sea".equals(p.getTag("natural"));

			QuadTree goodCoords = (sea ? seaCoords : landCoords);
			QuadTree badCoords = (sea ? landCoords : seaCoords);
			String polyType = (sea ? "sea" : "land");
			String otherType = (sea ? "land" : "sea");
			
			List<Coord> minusCoords = badCoords.get(p.getPoints(),
					getFloodBlockerGap());
			List<Coord> positiveCoords = goodCoords.get(p.getPoints());
			
			log.info(polyType,"polygon", p.getId(), "contains",
					minusCoords.size(), otherType,"coords and",
					positiveCoords.size(), polyType,"coords.");	
			
			if (minusCoords.size() > 0) {
				double area = calcArea(p.getPoints());
				double ratio = ((minusCoords.size() - positiveCoords.size()) * 100000.0d / area);
				String areaFMT = format.format(area);
				String ratioFMT = format.format(ratio);
				log.info("Flood blocker for", polyType, "polygon", p.getId());
				log.info("area",areaFMT);
				log.info(polyType, positiveCoords.size());
				log.info(otherType, minusCoords.size());
				log.info("ratio", ratioFMT);
				if (debug) {
					GpxCreator.createGpx(
							baseName + p.getId() + "_"+polyType+"_"
									+ minusCoords.size() + "_"
									+ positiveCoords.size() + "_" + ratioFMT,
							p.getPoints());
					GpxCreator.createGpx(
							baseName + p.getId() + "_con_"
									+ minusCoords.size() + "_"
									+ positiveCoords.size() + "_" + ratioFMT,
									null, minusCoords);

					if (positiveCoords.isEmpty() == false) {
						GpxCreator.createGpx(
								baseName + p.getId() + "_pro_"
										+ minusCoords.size() + "_"
										+ positiveCoords.size() + "_"
										+ ratioFMT, null,
								positiveCoords);
					}
				}

				if (minusCoords.size() - positiveCoords.size() >= getFloodBlockerThreshold()
						&& ratio > getFloodBlockerRatio()) {
					log.warn("Polygon", p.getId(), "type",polyType,"seems to be wrong. Changing it to",otherType);
					if (sea) {
						p.deleteTag("natural");
						p.addTag(landTag[0], landTag[1]);
					} else {
						p.deleteTag(landTag[0]);
						p.addTag("natural", "sea");
					}
//					getMpPolygons().remove(p.getId());
				} else {
					log.info("Polygon",p.getId(), "is not blocked");
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

	public boolean isFloodBlocker() {
		return floodBlocker;
	}

	public void setFloodBlocker(boolean floodBlocker) {
		this.floodBlocker = floodBlocker;
	}

	public int getFloodBlockerGap() {
		return floodBlockerGap;
	}

	public void setFloodBlockerGap(int floodBlockerGap) {
		this.floodBlockerGap = floodBlockerGap;
	}

	public double getFloodBlockerRatio() {
		return floodBlockerRatio;
	}

	public void setFloodBlockerRatio(double floodBlockerRatio) {
		this.floodBlockerRatio = floodBlockerRatio;
	}

	public int getFloodBlockerThreshold() {
		return floodBlockerThreshold;
	}

	public void setFloodBlockerThreshold(int floodBlockerThreshold) {
		this.floodBlockerThreshold = floodBlockerThreshold;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public Rule getFloodBlockerRules() {
		return floodBlockerRules;
	}

	public void setFloodBlockerRules(Rule floodBlockerRules) {
		this.floodBlockerRules = floodBlockerRules;
	}

	public void setLandTag(String landTag, String landValue) {
		this.landTag[0] = landTag;
		this.landTag[1] = landValue;
	}

}
