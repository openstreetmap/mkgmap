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

import java.io.IOException;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.io.StructuredInputStream;
import uk.me.parabola.io.StructuredOutputStream;

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

	private int mapNumber;
	private String mapName;
	private int parentMapNumber;

	private String description;

	private int maxLat;
	private int maxLong;
	private int minLat;
	private int minLong;

	public OverviewMapBlock() {
		description = "overview map";
	}

	public OverviewMapBlock(Block block) throws IOException {
		StructuredInputStream ds = block.getInputStream();

		mapNumber = ds.read4();
		parentMapNumber = ds.read4();

		maxLat = ds.read4();
		maxLong = ds.read4();
		minLat = ds.read4();
		minLong = ds.read4();

		description = ds.readString();
	}

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();

		os.write4(mapNumber);
		os.write4(parentMapNumber);
		os.write4(maxLat);
		os.write4(maxLong);
		os.write4(minLat);
		os.write4(minLong);
		os.writeString(description);
	}

	public String toString() {
		return "Overview: "
				+ mapNumber
				+ ", parent="
				+ parentMapNumber
				+ " covers "
				+ '(' + toDegrees(minLat) + ',' + toDegrees(minLong) + ')'
				+ '(' + toDegrees(maxLat) + ',' + toDegrees(maxLong) + ')'
				+ " : "
				+ description
				;
	}

	private double toDegrees(int tdbunits) {
		return (double) tdbunits * 360 / Math.pow(2, 32);
	}

	public void setArea(Area bounds) {
		minLat = bounds.getMinLat() << 8;
		minLong = bounds.getMinLong() << 8;
		maxLat = bounds.getMaxLat() << 8;
		maxLong = bounds.getMaxLong() << 8;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setMapName(String mapName) {
		this.mapName = mapName;
		try {
			this.mapNumber = Integer.parseInt(mapName);
		} catch (NumberFormatException e) {
			this.mapNumber = 0;
		}
	}

	protected String getMapName() {
		return mapName;
	}

	public void setParentMapNumber(int parentMapNumber) {
		this.parentMapNumber = parentMapNumber;
	}
}
