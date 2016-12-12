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

import uk.me.parabola.io.StructuredOutputStream;

import java.io.IOException;

/**
 * Block describing the map set.
 *
 * @author Steve Ratcliffe
 */
public class MapsetBlock extends MpsBlock {
	private static final int BLOCK_TYPE = 0x56;
	
	private String name = "OSM map set";
	
	public MapsetBlock() {
		super(BLOCK_TYPE, 0);
	}

	protected void writeBody(StructuredOutputStream out) throws IOException {
		out.writeString(name);
		out.write(0); // unknown
	}

	public void setName(String name) {
		if (name != null)
			this.name = name;
	}
}
