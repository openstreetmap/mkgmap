/*
 * Copyright (C) 2012.
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

import uk.me.parabola.util.EnhancedProperties;

/**
 * This saver stores elements loaded from precompiled sea tiles.
 * It does not convert so the data is still available after loading.
 * @author WanMil
 */
public class PrecompSeaElementSaver extends ElementSaver {

	public PrecompSeaElementSaver(EnhancedProperties args) {
		super(args);
	}

	public void convert(OsmConverter converter) {
	}
}