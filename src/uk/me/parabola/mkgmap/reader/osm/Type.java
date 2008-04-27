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
 * Create date: Apr 25, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.log.Logger;

/**
 * @author Steve Ratcliffe
 */
public class Type implements TypeResolver {
	private static final Logger log = Logger.getLogger(Type.class);

	private static int nextIndex;

	private final int index;
	private final int type;
	private final int subtype;
	private int minResolution;

	Type(String type) {
		int it;
		try {
			it = Integer.decode(type);
		} catch (NumberFormatException e) {
			log.error("not numeric " + type);
			it = 0;
		}
		this.type = it;
		this.subtype = 0;
		this.index = getNextIndex();
	}

	Type(String type, String subtype) {
		int it;
		int ist;
		try {
			it = Integer.decode(type);
			ist = Integer.decode(subtype);
		} catch (NumberFormatException e) {
			log.error("not numeric " + type + ' ' + subtype);
			it = 0;
			ist = 0;
		}
		this.type = it;
		this.subtype = ist;
		this.index = getNextIndex();
	}

	public Type resolveType(Element el) {
		return this;
	}

	public int getType() {
		return type;
	}

	public int getSubtype() {
		return subtype;
	}

	public int getMinResolution() {
		return minResolution;
	}

	public void setMinResolution(int minResolution) {
		this.minResolution = minResolution;
	}

	public static int getNextIndex() {
		return nextIndex++;
	}

	public boolean isBetter(Type other) {
		return index < other.getIndex();
	}

	public int getIndex() {
		return index;
	}
}
