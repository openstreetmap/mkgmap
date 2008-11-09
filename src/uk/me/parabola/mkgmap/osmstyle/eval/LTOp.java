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
 * Create date: 09-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Less than.  For population speeds etc.
 * @author Steve Ratcliffe
 */
public class LTOp extends EqualsOp {
	public LTOp() {
		setType(LT);
	}

	public boolean eval(Element el) {
		ValueWithUnit tagValue = getUnitValue(el, getFirst().value());
		if (tagValue == null)
			return false;

		ValueWithUnit ourVal = new ValueWithUnit(getSecond().value());

		if (!tagValue.isValid() || !ourVal.isValid())
			return false;
		return (tagValue.compareTo(ourVal) < 0);
	}
}