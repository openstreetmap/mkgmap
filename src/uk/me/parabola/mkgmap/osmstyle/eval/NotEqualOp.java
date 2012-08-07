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
public class NotEqualOp extends EqualsOp {
	public NotEqualOp() {
		setType(NOT_EQUALS);
	}

	public boolean eval(Element el) {
		return !super.eval(el);
	}

	public String getTypeString() {
		return "!=";
	}

	public String toString() {
		return getFirst().toString() + "!=" + getSecond();
	}
}
