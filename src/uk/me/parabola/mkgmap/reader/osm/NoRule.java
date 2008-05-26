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
 * Create date: Apr 27, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

/**
 * This just wraps a type and always returns it.  An unconditional rule that
 * is used for lines that were read from map-features, or that have no
 * complex conditions.
 *
 * @author Steve Ratcliffe
 */
public class NoRule extends BaseRule implements TypeRule {
	private GType gt;

	public NoRule(GType gt) {
		this.gt = gt;
	}

	public GType resolveType(Element el) {
		return gt;
	}
}
