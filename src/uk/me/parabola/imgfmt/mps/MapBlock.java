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
 * Create date: Dec 19, 2007
 */
package uk.me.parabola.imgfmt.mps;

import java.io.IOException;

import uk.me.parabola.io.StructuredOutputStream;

/**
 * A block describing an individual map.
 * 
 * @author Steve Ratcliffe
 */
public class MapBlock extends Block {
	private static final int BLOCK_TYPE = 0x4c;
	
	private int productId; // For historical reasons this is combined family/product id. TODO split
	private int mapNumber;
	private String seriesName;
	private String mapName;
	private String areaName;

	public MapBlock() {
		super(BLOCK_TYPE);
	}

	protected void writeBody(StructuredOutputStream out) throws IOException {
		out.write4(productId);
		out.write4(mapNumber);
		out.writeString(seriesName);
		out.writeString(mapName);
		out.writeString(areaName);
		out.write4(mapNumber);
		out.write4(0);
	}

	public void setIds(int familyId, int productId) {
		this.productId = (familyId << 16) | productId;
	}

	public void setTypeName(String seriesName) {
		this.seriesName = seriesName;
	}

	public void setMapNumber(int mapNumber) {
		this.mapNumber = mapNumber;
	}

	public void setMapName(String mapName) {
		this.mapName = mapName;
	}

	public void setAreaName(String areaName) {
		this.areaName = areaName;
	}

	public int getFamilyId() {
		return productId >>> 16;
	}

	public int getProductId() {
		return productId & 0xffff;
	}

	public int getMapNumber() {
		return mapNumber;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public String getMapName() {
		return mapName;
	}

	public String getAreaName() {
		return areaName;
	}
}
