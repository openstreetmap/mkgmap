/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.reader.osm;

/**
 * At the top level we need to be able to watch to see if a result was found
 * to implement stop-on-first-match, continue and the like.
 *
 * @author Steve Ratcliffe
 */
public class WatchableTypeResult implements TypeResult {
	private boolean found;
	private boolean continued;

	private final TypeResult result;

	public WatchableTypeResult(TypeResult result) {
		this.result = result;
	}

	public void add(Element el, GType type) {
		if (type == null)
			return;
		
		if (type.isContinueSearch())
			continued = true;

		found = true;
		result.add(el, type);
	}

	public boolean isFound() {
		return found;
	}

	public boolean isContinued() {
		return continued;
	}

	public boolean isResolved() {
		return found && !continued;
	}

	public void reset() {
		continued = false;
		found = false;
	}
}
