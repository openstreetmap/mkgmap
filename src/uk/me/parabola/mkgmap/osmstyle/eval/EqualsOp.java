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
 * Create date: 03-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Holds tag=value relationship.
 * 
 * @author Steve Ratcliffe
 */
public class EqualsOp extends BinaryOp {
	public EqualsOp() {
		setType(EQUALS);
	}

	public boolean eval(Element el) {
		String key = getFirst().toString();
		String value = getSecond().toString();

		String s = el.getTag(key);
		if (s == null)
			return false;
		return s.equals(value);
	}

	public int priority() {
		return 10;
	}

	public String toString() {
		return getFirst().toString() + getTypeRep() + getSecond();
	}

	/**
	 * Get the value of a tag and split it into a numeric part and a unit.
	 * It tries the best it can.  This is to do things like 20mph etc.
	 * @return A class containing the value and the unit it is in if found.
	 * @see ValueWithUnit
	 */
	protected ValueWithUnit getUnitValue(Element el, String key) {
		String val = el.getTag(key);
		return new ValueWithUnit(val);
	}

	@SuppressWarnings({"MethodWithMultipleReturnPoints"})
	private String getTypeRep() {
		switch (getType()) {
		case EQUALS: return "=";
		case NOT_EQUALS: return "!=";
		case GT: return ">";
		case GTE: return ">=";
		case LT: return "<";
		case LTE: return "<=";
		default: return "?";
		}
	}
}
