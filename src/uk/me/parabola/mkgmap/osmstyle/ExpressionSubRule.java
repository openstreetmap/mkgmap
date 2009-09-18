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
 * A rule that contains a condition and another rule.  If the condition is
 * matched by the element then the held rule is run and forms the result.
 *
 * @author Steve Ratcliffe
 */
public class ExpressionSubRule implements Rule {
	private final Op exression;
	private final Rule rule;

	public ExpressionSubRule(Op exression, Rule rule) {
		this.exression = exression;
		this.rule = rule;
	}

	public GType resolveType(Element el) {
		if (exression.eval(el))
			return rule.resolveType(el);

		return null;
	}

	public String toString() {
		return exression.toString() + '&' + rule;
	}
}