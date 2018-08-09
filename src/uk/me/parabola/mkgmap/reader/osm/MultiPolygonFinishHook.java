/*
 * Copyright (C) 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Arrays;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

public class MultiPolygonFinishHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(MultiPolygonFinishHook.class);
	
	private ElementSaver saver;
	
	public MultiPolygonFinishHook() {
	}

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		return true;
	}

	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Finishing multipolygons");
		for (Way way : saver.getWays().values()) {
			String removeTag = way.getTag(ElementSaver.MKGMAP_REMOVE_TAG_KEY);
			if (removeTag == null) {
				continue;
			}
			String[] tagsToRemove = removeTag.split(";");
			if (log.isDebugEnabled()) {
				log.debug("Remove tags",Arrays.toString(tagsToRemove),"from way",way.getId(),way.toTagString());
			}
			way.deleteTag(ElementSaver.MKGMAP_REMOVE_TAG_KEY);
			for (String rTag : tagsToRemove) {
				way.deleteTag(rTag);
			}
		}
		log.info("Multipolygon hook finished in "+(System.currentTimeMillis()-t1)+" ms");

	}

}
