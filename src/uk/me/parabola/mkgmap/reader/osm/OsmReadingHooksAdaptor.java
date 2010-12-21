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

import java.util.Collections;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Provides empty implementation of all methods so that subclass that only
 * need a few can just implement the ones they want.
 * 
 * @author Steve Ratcliffe
 */
public class OsmReadingHooksAdaptor implements OsmReadingHooks {
	
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		return true;
	}

	public Set<String> getUsedTags() {
		return Collections.emptySet();
	}
	
	public void onAddNode(Node node) {
	}

	public void onAddWay(Way way) {
	}

	public void onCoordAddedToWay(Way way, long coordId, Coord co) {
	}

	public void end() {
	}
}
