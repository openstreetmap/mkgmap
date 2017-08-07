/*
 * Copyright (C) 2017.
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
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

public class NotRegexOp extends RegexOp {
	public NotRegexOp() {
		setType(NodeType.NOT_REGEX);
	}

	public boolean eval(Element el) {
		return !super.eval(el);
	}

	public String toString() {
		return getFirst().toString() + "!~" + getSecond();
	}
}
