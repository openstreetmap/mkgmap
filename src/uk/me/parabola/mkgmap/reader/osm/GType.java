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

import java.util.Formatter;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;

/**
 * Holds the garmin type of an element and all the information that
 * will be needed to represent it on the map.  So we have a range of
 * resolutions at which it will be present.
 *
 * @author Steve Ratcliffe
 */
public class GType {
	private static final Logger log = Logger.getLogger(GType.class);

	public static final int POINT = 1;
	public static final int POLYLINE = 2;
	public static final int POLYGON = 3;

	private final int featureKind;
	private final int type;
	private final int subtype;

	private int index;

	private int minResolution;
	private int maxResolution;

	private String defaultName;

	public GType(int featureKind, String type) {
		this.featureKind = featureKind;
		try {
			int t = Integer.decode(type);
			if (t > 0xff) {
				this.type = t >> 8;
				this.subtype = t & 0xff;
			} else {
				this.type = t;
				this.subtype = 0;
			}
		} catch (NumberFormatException e) {
			log.error("not numeric " + type);
			throw new ExitException("non-numeric type in map-features file");
		}
	}

	public GType(int featureKind, String type, String subtype) {
		this.featureKind = featureKind;
		try {
			this.type = Integer.decode(type);
			this.subtype = Integer.decode(subtype);
		} catch (NumberFormatException e) {
			log.error("not numeric " + type + ' ' + subtype);
			throw new ExitException("non-numeric type in map-features file");
		}
	}

	public int getFeatureKind() {
		return featureKind;
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

	public int getMaxResolution() {
		return maxResolution;
	}

	public void setMaxResolution(int maxResolution) {
		this.maxResolution = maxResolution;
	}

	public boolean isBetter(GType other) {
		return index < other.getIndex();
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getDefaultName() {
		return defaultName;
	}

	public void setDefaultName(String defaultName) {
		this.defaultName = defaultName;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		Formatter fmt = new Formatter(sb);
		sb.append('[');
		fmt.format("%#x", type);
		// TODO more...
		sb.append(']');
		return sb.toString();
	}
}
