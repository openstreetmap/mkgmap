/*
 * Copyright (C) 2008-2012 Steve Ratcliffe
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
 * Create date: 10-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Class for numeric operations.
 *
 * This may include Equals at some point so that you can do max_speed=20mph
 * even when the tag doesn't include mph etc.
 *
 * @author Steve Ratcliffe
 */
public abstract class NumericOp extends AbstractBinaryOp {

	/**
	 * This is passed the result of a compareTo and the subclass
	 * returns true or false depending on the operation.
	 */
	protected abstract boolean doesCompare(int result);

	public int priority() {
		return 10;
	}

	/**
	 * This evaluation routine works for all numeric tests.  Implement the
	 * {@link #doesCompare} routine instead of this.
	 */
	public final boolean eval(Element el) {
		// get the value of the tag, if it doesn't exist then false.
		String val = first.value(el);
		if (val == null)
			return false;

		ValueWithUnit result = new ValueWithUnit(val);
		String val2 = getSecond().value(el);
		if (val2 == null)
			return false;
		ValueWithUnit ourVal = new ValueWithUnit(val2);

		if (!result.isValid() || !ourVal.isValid())
			return false;
		int inter = result.compareTo(ourVal);
		return doesCompare(inter);
	}

	public String toString() {
		return first + getType().toSymbol() + getSecond();
	}
}
