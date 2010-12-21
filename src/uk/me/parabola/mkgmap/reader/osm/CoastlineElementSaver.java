package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.util.EnhancedProperties;

/**
 * This saver only keeps ways with <code>natural=coastline</code> tags. 
 * This is used for loading of extra coastline files. 
 * @author WanMil
 */
public class CoastlineElementSaver extends ElementSaver {

	public CoastlineElementSaver(EnhancedProperties args) {
		super(args);
	}

	@Override
	public void addNode(Node node) {
		return;
	}

	@Override
	public void addWay(Way way) {
		String tag = way.getTag("natural");
		if (tag != null && tag.contains("coastline")) {
			// remove all tags => the natural=coastline is implicitly known
			way.removeAllTags();
			super.addWay(way);
		}
	}

	@Override
	public void addRelation(Relation rel) {
		return;
	}

	@Override
	public void convert(OsmConverter converter) {
		return;
	}
	
	
}