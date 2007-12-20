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

import uk.me.parabola.tdbfmt.StructuredOutputStream;

import java.io.IOException;

/**
 * A block describing an individual map.
 * 
 * @author Steve Ratcliffe
 */
public class MapBlock extends Block {
	private int productId;
	private int mapNumber;
	private String typeName;
	private String mapName;
	private String areaName;

	public MapBlock(int type) {
		super(type);
	}

	protected void writeBody(StructuredOutputStream out) throws IOException {
		out.write4(productId);
		out.write4(mapNumber);
		out.writeString(typeName);
		out.writeString(mapName);
		out.writeString(areaName);
		out.write4(mapNumber);
		out.write4(0);
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
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
}
