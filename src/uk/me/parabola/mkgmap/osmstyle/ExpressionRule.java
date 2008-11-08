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

import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;

/**
 * A rule that contains a condition.  If the condition is matched by the
 * element then the held gtype is returned.
 * 
 * @author Steve Ratcliffe
 */
public class ExpressionRule implements Rule {
	private Op exression;
	private GType gtype;

	public ExpressionRule(Op exression, GType gtype) {
		this.exression = exression;
		this.gtype = gtype;
	}

	public GType resolveType(Element el) {
		if (exression.eval(el))
			return gtype;

		return null;
	}

	public String toString() {
		return "COND(" + gtype + ')';
	}
}
