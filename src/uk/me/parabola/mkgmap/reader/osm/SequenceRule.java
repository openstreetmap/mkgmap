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

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve Ratcliffe
 */
public class SequenceRule extends BaseRule implements TypeRule {
	private final List<TypeRule> list = new ArrayList<TypeRule>();

	public GType resolveType(Element el) {
		for (TypeRule rule : list) {
			GType gt = rule.resolveType(el);
			if (gt != null)
				return gt;
		}
		return null;
	}

	public void add(TypeRule type) {
		list.add(type);
	}
}
