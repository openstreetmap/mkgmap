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
	private boolean actionsOnly;
	private boolean found;
	private boolean continued;
	private int count;

	private final TypeResult result;

	public WatchableTypeResult(TypeResult result) {
		this.result = result;
	}

	public void add(Element el, GType type) {
		if (type == null) {
			actionsOnly = true;
			return;
		}
		
		if (type.isContinueSearch())
			continued = true;

		count++;
		found = true;
		result.add(el, type);
	}

	/**
	 * Was a result found.
	 * @return True if one or more results were added since the last reset.
	 */
	public boolean isFound() {
		return found;
	}

	/**
	 * Are we all done for this element?
	 * @return True if we found a matching type and it did not have the continue
	 * flag set.
	 */
	public boolean isResolved() {
		return found && !continued;
	}

	public boolean isActionsOnly() {
		return actionsOnly;
	}

	public int getCount() {
		return count;
	}

	/**
	 * Reset the watcher for the next element.
	 */
	public void reset() {
		count = 0;
		actionsOnly = false;
		continued = false;
		found = false;
	}
}
