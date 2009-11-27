/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 07-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * A rule that always returns the same gtype.
 *
 * @author Steve Ratcliffe
 */
public class FixedRule implements Rule {
	private final GType gtype;

	public FixedRule(GType gtype) {
		this.gtype = gtype;
	}

	public GType resolveType(Element el, GType pre) {
		return gtype;
	}

	public String toString() {
		return gtype.toString();
	}
}
