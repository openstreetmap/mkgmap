/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.tdbfmt;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;

import java.io.IOException;

/**
 * The overview map provides a low-detail image for the detailed maps.  It
 * allows you to see what areas the detail maps cover so they can be selected
 * in programs such as QLandkarte and Garmin's MapSource.
 *
 * In addition to a low detail map, the overview map contains a number of type
 * 0x4a polygons.  These definition areas a labeled after and correspond to
 * the detail map img files.
 *
 * The detail maps contain a background polygon (type 0x4b) that matches the
 * definition area in the overview map.
 * 
 * @author Steve Ratcliffe
 */
public class OverviewMapBlock {
	private static final Logger log = Logger.getLogger(OverviewMapBlock.class);
	
	private int mapNumber;
	private int parentMapNumber;
	private Area area;
	private String description;

	public OverviewMapBlock(Block block) throws IOException {
		StructuredInputStream ds = block.getStream();

		mapNumber = ds.read4();
		parentMapNumber = ds.read4();

		int maxLat = ds.read4() >> 8;
		int maxlong = ds.read4() >> 8;
		int minLat = ds.read4() >> 8;
		int minLong = ds.read4() >> 8;

		log.debug(minLat, minLong, maxLat, maxlong);
		area = new Area(minLat, minLong, maxLat, maxlong);
		description = ds.readString();
	}

	public String toString() {
		return "Overview: "
				+ mapNumber
				+ ", parent="
				+ parentMapNumber
				+ " covers "
				+ area
				+ " : "
				+ description
				;
	}
}
