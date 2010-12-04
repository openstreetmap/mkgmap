package uk.me.parabola.mkgmap.reader.osm.bin;

import java.util.Collections;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.CoastlineElementSaver;
import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;

public class OsmBinCoastDataSource extends OsmBinMapDataSource {
	
	private static final Set<String> coastlineTags = Collections.singleton("natural");

	@Override
	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
		return;
	}

	protected OsmReadingHooks[] getPossibleHooks() {
		// no hooks
		return new OsmReadingHooks[] {};
	}

	@Override
	protected void createElementSaver() {
		elementSaver = new CoastlineElementSaver(getConfig());
	}

	@Override
	public Set<String> getUsedTags() {
		return coastlineTags;
	}
}
