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
 * Create date: 23-Jun-2008
 */
package uk.me.parabola.tdbfmt;

import java.io.IOException;

import uk.me.parabola.io.StructuredOutputStream;

/**
 * @author Steve Ratcliffe
 */
public class RBlock {
	private final String previewDescription = "Test preview map";

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();

		os.write(0xc3);
		os.writeString(previewDescription);
	}
}
