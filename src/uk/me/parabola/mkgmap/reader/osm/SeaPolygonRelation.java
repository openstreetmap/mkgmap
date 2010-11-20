package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	
	public SeaPolygonRelation(Relation other, Map<Long, Way> wayMap,
			Map<Long, Set<String>> wayRemoveTags,
			uk.me.parabola.imgfmt.app.Area bbox) {
		super(other, wayMap, wayRemoveTags, bbox);
		this.landCoords = new QuadTree(bbox);
		this.seaCoords = new QuadTree(bbox);
		// set a special type because this is not the OSM common multipolygon
		// relation
		addTag("type", "mkgmap:seapolygon");
	}

	public void setLandCoords(Collection<Coord> landCoords) {
		this.landCoords.addAll(landCoords);
	}

	public void setSeaCoords(Collection<Coord> seaCoords) {
		this.seaCoords.addAll(seaCoords);
	}

	@Override
	protected void postProcessing() {
		removeFloodedAreas();
		super.postProcessing();
	}

	private void removeFloodedAreas() {
		// create a copy of all resulting ways - the tile way map contains only
		// polygons from
		// the sea generation
		ArrayList<Way> polygons = new ArrayList<Way>(getMpPolygons().values());

		log.info("Starting flood blocker. Polygons to check:",getMpPolygons().size());
		// go through all polygons and check if it contains too many coords of
		// the other type
		for (Way p : polygons) {
			boolean sea = "sea".equals(p.getTag("natural"));
			if (sea) {
				List<Coord> vetoCoords = landCoords.get(p.getPoints());
				log.info("Sea polygon", p.getId(), "contains",
						vetoCoords.size(), "land coords.");
				if (vetoCoords.isEmpty() == false) {
					log.warn("Flood blocker for sea polygon with center", p
							.getCofG().toOSMURL());
					Way x = getMpPolygons().remove(p.getId());
					if (x==null) {
						log.error("Way "+p.getId()+" could not be removed.");
					}
				}
			} else {
				List<Coord> vetoCoords = seaCoords.get(p.getPoints());
				log.info("Land polygon", p.getId(), "contains",
						vetoCoords.size(), "sea coords.");
				if (vetoCoords.isEmpty() == false) {
					log.warn("Flood blocker for land polygon with center", p
							.getCofG().toOSMURL());
					getMpPolygons().remove(p.getId());
				}
			}
		}
		log.info("Flood blocker finished. Resulting polygons:",getMpPolygons().size());
	}

}
