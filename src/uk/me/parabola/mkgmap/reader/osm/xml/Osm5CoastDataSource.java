package uk.me.parabola.mkgmap.reader.osm.xml;

import java.util.Collections;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.CoastlineElementSaver;
import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;

public class Osm5CoastDataSource extends Osm5MapDataSource {

	private static final Set<String> coastlineTags = Collections.singleton("natural");
	
	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
	}
	
	protected OsmReadingHooks[] getPossibleHooks() {
		// no hooks
		return new OsmReadingHooks[] {};
	}

	protected void createElementSaver() {
		elementSaver = new CoastlineElementSaver(
				getConfig());
	}

	public Set<String> getUsedTags() {
		return coastlineTags;
	}
}
