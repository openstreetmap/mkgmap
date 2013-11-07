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
 * Holds a string value; the name of a tag or the value of a tag for example.
 * 
 * @author Steve Ratcliffe
 */
public class ValueOp extends AbstractOp {
	private final String value;

	public ValueOp(String value) {
		setType(NodeType.VALUE);
		this.value = value;
	}

	public boolean eval(Element el) {
		return true;
	}

	public int priority() {
		return 0;
	}

	public String value(Element el) {
		return value;
	}

	/**
	 * Get the saved value.
	 * For a base ValueOp this returns the same as {@link #value} but in classes where value is
	 * overridden it returns the base value.
	 */
	public final String getKeyValue() {
		return value;
	}

	public boolean isValue(String val) {
		return value.equals(val);
	}

	/**
	 * Returns true if you can index the rule from this value.
	 * This should almost always return false, override in subclasses that are indexable.
	 */
	public boolean isIndexable() {
		return false;
	}

	public String toString() {
		return value;
	}
}
