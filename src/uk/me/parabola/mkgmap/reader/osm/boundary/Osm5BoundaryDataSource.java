package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.MultiPolygonFinishHook;
import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5MapDataSource;
import uk.me.parabola.util.EnhancedProperties;

public class Osm5BoundaryDataSource extends Osm5MapDataSource {

	private static final Set<String> boundaryTags = new HashSet<String>() {
		{
			add("type");
			add("boundary");
			add("admin_level");
			add("name");
			add("ref");
		}
	};
	
	private final List<Boundary> boundaries = new ArrayList<Boundary>();
	
	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
	}
	
	protected OsmReadingHooks[] getPossibleHooks() {
		return new OsmReadingHooks[] { new MultiPolygonFinishHook()};
	}

	protected void createElementSaver() {
		elementSaver = new BoundaryElementSaver(getConfig());
	}

	public Set<String> getUsedTags() {
		return boundaryTags;
	}

	protected void createConverter() {
		converter = new BoundaryConverter(getBoundaries());
	}

	public List<Boundary> getBoundaries() {
		return boundaries;
	}

	private final EnhancedProperties props = new EnhancedProperties();
	protected EnhancedProperties getConfig() {
		return props;
	}
	
	
}
