package uk.me.parabola.mkgmap.reader.osm;

import java.util.Arrays;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

public class MultiPolygonFinishHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(MultiPolygonFinishHook.class);
	
	private ElementSaver saver;
	
	public MultiPolygonFinishHook() {
	}

	@Override
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		return true;
	}

	@Override
	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Finishing multipolygons");
		for (Way way : saver.getWays().values()) {
			String removeTag = way.getTag(ElementSaver.MKGMAP_REMOVE_TAG);
			if (removeTag == null) {
				continue;
			}
			if (ElementSaver.MKGMAP_REMOVE_TAG_ALL_KEY.equals(removeTag)) {
				if (log.isDebugEnabled())
					log.debug("Remove all tags from way",way.getId(),way.toTagString());
				way.removeAllTags();
			} else {
				String[] tagsToRemove = removeTag.split(";");
				if (log.isDebugEnabled())
					log.debug("Remove tags",Arrays.toString(tagsToRemove),"from way",way.getId(),way.toTagString());
				for (String rTag : tagsToRemove) {
					way.deleteTag(rTag);
				}
				way.deleteTag(ElementSaver.MKGMAP_REMOVE_TAG);
			}
		}
		log.info("Multipolygon hook finished in "+(System.currentTimeMillis()-t1)+" ms");

	}

}
