/*
 * Copyright (C) 2010.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.EnhancedProperties;

/**
 * A single class that holds several {@link OsmReadingHooks} and calls them in
 * order.
 *
 * It implements {@link OsmReadingHooks} itself.
 *
 * @author Steve Ratcliffe
 */ 
@SuppressWarnings({"ForLoopReplaceableByForEach"})
public class OsmReadingHooksChain implements OsmReadingHooks {

	private static final OsmReadingHooks[] NO_HOOKS = new OsmReadingHooks[0];
	
	private OsmReadingHooks[] readingHooks = NO_HOOKS;

	/**
	 * Add a new set of hooks.
	 * @param hooks The reading hooks.
	 */
	public void add(OsmReadingHooks hooks) {
		List<OsmReadingHooks> readingHooksList = new ArrayList<OsmReadingHooks>(Arrays.asList(readingHooks));
		readingHooksList.add(hooks);
		readingHooks = readingHooksList.toArray(new OsmReadingHooks[readingHooksList.size()]);
	}
	
	public Set<String> getUsedTags() {
		HashSet<String> usedTags = new HashSet<String>();
		for (int i = 0; i < readingHooks.length; i++)
			usedTags.addAll(readingHooks[i].getUsedTags());
		return usedTags;
	}
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		for (int i = 0; i < readingHooks.length; i++)
			readingHooks[i].init(saver, props);
		return true;
	}

	public void onAddNode(Node node) {
		for (int i = 0; i < readingHooks.length; i++)
			readingHooks[i].onAddNode(node);
	}

	public void onCoordAddedToWay(Way way, long coordId, Coord co) {
		for (int i = 0; i < readingHooks.length; i++)
			readingHooks[i].onCoordAddedToWay(way, coordId, co);
	}

	public void onAddWay(Way way) {
		for (int i = 0; i < readingHooks.length; i++)
			readingHooks[i].onAddWay(way);
	}

	public void end() {
		for (int i = 0; i < readingHooks.length; i++)
			readingHooks[i].end();
	}
}
