/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * Base class for OSM file handlers.
 * 
 * @author Steve Ratcliffe
 */
public class OsmHandler {
	// Elements that are read are saved/further processed by these two classes.
	protected ElementSaver saver;
	protected OsmReadingHooks hooks;

	private final Map<String, Long> fakeIdMap = new HashMap<String, Long>();
	private Map<String,Set<String>> deletedTags;
	private Map<String, String> usedTags;

	// Node references within a way
	protected long firstNodeRef;
	protected long lastNodeRef;
	protected boolean missingNodeRef;

	/**
	 * Set a set of tags with values that are to be deleted on input.
	 * For each key there is a set of values.  If the value set is empty then
	 * all tags with the given key are deleted.  If the value set is not empty
	 * then only tags with the given key that has one of the given values are
	 * deleted.
	 *
	 * @param deletedTags A map of tag key, to a set of values to be deleted.
	 */
	public void setTagsToDelete(Map<String, Set<String>> deletedTags) {
		this.deletedTags = deletedTags;
	}

	/**
	 * This sets a list of all the tags that are used in the system.
	 *
	 * Assuming this list is complete, no other tag can have an effect on the output
	 * and can therefore be dropped on input. This reduces memory usage, sometimes
	 * dramatically if there are many useless tags in the input.
	 *
	 * We keep a map of tag-name to tag-name.  This allows us to keep only a single
	 * copy of each string.  This also results in a reasonable reduction in memory usage.
	 *
	 * @param used The complete set of tags that are used to form the output.
	 */
	public void setUsedTags(Set<String> used) {
		if (used == null || used.isEmpty()) {
			usedTags = null;
			return;
		}
		usedTags = new HashMap<String, String>();
		for (String s : used)
			usedTags.put(s, s);
	}

	/**
	 * Some tags are dropped at the input stage.  We drop tags that are not going
	 * to be used and there is also an option to provide a file containing tags to
	 * be dropped.
	 *
	 * @param key The tag key.
	 * @param val The tag value.
	 * @return Returns the tag key if this tag should be kept.  Returns null if the tag
	 * should be discarded.
	 */
	protected String keepTag(String key, String val) {
		if(deletedTags != null) {
			Set<String> vals = deletedTags.get(key);
			if(vals != null && (vals.isEmpty() || vals.contains(val))) {
				return null;
			}
		}

		// By returning the value stored in usedTags, instead of the key, we ensure
		// that the same string is always used so saving some memory.
		if (usedTags != null)
			return usedTags.get(key);

		return key;
	}

	/**
	 * Actually set the bounding box.  The boundary values are given.
	 */
	protected void setBBox(double minlat, double minlong, double maxlat, double maxlong) {
		Area bbox = new Area(minlat, minlong, maxlat, maxlong);
		saver.setBoundingBox(bbox);
	}

	/**
	 * Convert an id as a string to a number. If the id is not a number, then create
	 * a unique number instead.
	 * @param id The id as a string. Does not have to be a numeric quantity.
	 * @return A long id, either parsed from the input, or a unique id generated internally.
	 */
	protected long idVal(String id) {
		try {
			// attempt to parse id as a number
			return Long.parseLong(id);
		} catch (NumberFormatException e) {
			// if that fails, fake a (hopefully) unique value
			Long fakeIdVal = fakeIdMap.get(id);
			if(fakeIdVal == null) {
				fakeIdVal = FakeIdGenerator.makeFakeId();
				fakeIdMap.put(id, fakeIdVal);
			}
			//System.out.printf("%s = 0x%016x\n", id, fakeIdVal);
			return fakeIdVal;
		}
	}

	public void setElementSaver(ElementSaver elementSaver) {
		this.saver = elementSaver;
	}

	public void setHooks(OsmReadingHooks plugin) {
		this.hooks = plugin;
	}

	/**
	 * Common actions to take when creating a new way.
	 * Reset some state and create the Way object.
	 * @param id The osm id of the new way.
	 * @return The new Way itself.
	 */
	protected Way startWay(long id) {
		firstNodeRef = 0;
		lastNodeRef = 0;
		missingNodeRef = false;
		return new Way(id);
	}

	/**
	 * Common actions to take when a way has been completely read by the parser.
	 * It is saved
	 * @param way The way that was read.
	 */
	protected void endWay(Way way) {
		way.setClosed(firstNodeRef == lastNodeRef);
		way.setComplete(!missingNodeRef);

		saver.addWay(way);
		hooks.onAddWay(way);
	}

	/**
	 * Add a coordinate point to the way.
	 * @param way The Way.
	 * @param id The coordinate id.
	 */
	protected void addCoordToWay(Way way, long id) {
		lastNodeRef = id;
		if (firstNodeRef == 0) firstNodeRef = id;

		Coord co = saver.getCoord(id);

		if (co != null) {
			hooks.onCoordAddedToWay(way, id, co);
			co = saver.getCoord(id);
			way.addPoint(co);

			// nodes (way joins) will have highwayCount > 1
			co.incHighwayCount();
		} else {
			missingNodeRef = true;
		}
	}
}
