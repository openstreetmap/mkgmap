/*
 * Copyright (C) 2012.
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

package uk.me.parabola.mkgmap.osmstyle.function;

import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * The interface for all functions that can be used within a style file.<br>
 * The input parameter of a function is one element. The resulting value is a
 * string which can carry number values.
 * @author WanMil
 */
public abstract class StyleFunction extends ValueOp {

	public StyleFunction(String value) {
		super(value);
		setType(FUNCTION);
	}

	/**
	 * Retrieves if the function accepts {@link Node} objects as input parameter.
	 *
	 * @return {@code true} {@link Node} objects are supported; {@code false} .. are not supported
	 */
	public boolean supportsNode() {
		return false;
	}

	/**
	 * Retrieves if the function accepts {@link Way} objects as input parameter.
	 *
	 * @return {@code true} {@link Way} objects are supported; {@code false} .. are not supported
	 */
	public boolean supportsWay() {
		return false;
	}

	/**
	 * Retrieves if the function accepts {@link Relation} objects as input parameter.
	 *
	 * @return {@code true} {@link Relation} objects are supported; {@code false} .. are not supported
	 */
	public boolean supportsRelation() {
		return false;
	}

	/**
	 * Retrieves the function name. This is the part without function brackets (). It is case sensitive but should be lower
	 * case.
	 *
	 * @return the function name (e.g. length for length())
	 */
	public String getName() {
		return getKeyValue();
	}
}
