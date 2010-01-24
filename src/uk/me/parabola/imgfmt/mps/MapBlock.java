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
 * The family id, product id, series name, area name and map description can
 * be set per map.
 *
 * @author Steve Ratcliffe
 */
public class MapBlock extends Block {
	private static final int BLOCK_TYPE = 0x4c;

	private int familyId;
	private int productId;

	private int mapNumber;
	private String seriesName;
	private String mapDescription;
	private String areaName;

	public MapBlock() {
		super(BLOCK_TYPE);
	}

	protected void writeBody(StructuredOutputStream out) throws IOException {
		out.write2(productId);
		out.write2(familyId);
		out.write4(mapNumber);
		out.writeString(seriesName);
		out.writeString(mapDescription);
		out.writeString(areaName);
		out.write4(mapNumber);
		out.write4(0);
	}

	public void setIds(int familyId, int productId) {
		this.familyId = familyId;
		this.productId = productId;
	}

	public void setSeriesName(String seriesName) {
		this.seriesName = seriesName;
	}

	public void setMapNumber(int mapNumber) {
		this.mapNumber = mapNumber;
	}

	public void setMapDescription(String mapDescription) {
		this.mapDescription = mapDescription;
	}

	public void setAreaName(String areaName) {
		this.areaName = areaName;
	}

	public int getFamilyId() {
		return familyId;
	}

	public int getProductId() {
		return productId;
	}

	public int getMapNumber() {
		return mapNumber;
	}

	public String getSeriesName() {
		return seriesName;
	}

	public String getMapDescription() {
		return mapDescription;
	}

	public String getAreaName() {
		return areaName;
	}
}
