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
 * Create date: 08-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * True when the tag does not have the given value.
 *
 * @author Steve Ratcliffe
 */
public class NotEqualOp extends BinaryOp {
	public NotEqualOp() {
		setType(NOT_EQUALS);
	}

	public boolean eval(Element el) {
		String key = getFirst().toString();
		String value = getSecond().toString();

		String s = el.getTag(key);
		if (s == null)
			return false;
		return !s.equals(value);
	}

	public int priority() {
		return 10;
	}
}
