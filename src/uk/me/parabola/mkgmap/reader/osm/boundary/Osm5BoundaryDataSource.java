package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.MultiPolygonFinishHook;
import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5MapDataSource;
import uk.me.parabola.util.EnhancedProperties;

public class Osm5BoundaryDataSource extends Osm5MapDataSource {

	private final BoundarySaver saver;
	public Osm5BoundaryDataSource(BoundarySaver saver) {
		this.saver=saver;
	}
	
	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
	}
	
	protected OsmReadingHooks[] getPossibleHooks() {
		return new OsmReadingHooks[] { new MultiPolygonFinishHook()};
	}

	protected void createElementSaver() {
		elementSaver = new BoundaryElementSaver(getConfig(), saver);
	}

	public Set<String> getUsedTags() {
		// return null => all tags are used
		return null;
	}

	protected void createConverter() {
		converter = new BoundaryConverter(saver);
	}

	private final EnhancedProperties props = new EnhancedProperties();
	protected EnhancedProperties getConfig() {
		return props;
	}
	
	
}
