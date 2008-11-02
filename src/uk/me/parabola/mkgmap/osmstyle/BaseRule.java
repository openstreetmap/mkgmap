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
 * Create date: Apr 27, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import uk.me.parabola.mkgmap.reader.osm.TypeRule;

/**
 * @author Steve Ratcliffe
 */
public abstract class BaseRule implements TypeRule {
	private int index;

	public int getPriority() {
		return index;
	}

	public boolean isBetter(TypeRule other) {
		return this.getPriority() < other.getPriority();
	}

	public void setPriority(int index) {
		this.index = index;
	}
}
