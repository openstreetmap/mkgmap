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

package uk.me.parabola.mkgmap.osmstyle.function;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Checks if a way has been loaded completely or if some points are missing.
 * @author WanMil
 */
public class IsCompleteFunction extends AbstractFunction {

	public String getName() {
		return "is_complete";
	}

	public boolean supportsWay() {
		return true;
	}
	
	protected String calcImpl(Element el) {
		if (el instanceof Way) {
			return String.valueOf(((Way) el).isComplete());
		}
		return null;
	}

}
