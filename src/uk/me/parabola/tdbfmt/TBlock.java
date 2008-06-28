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
public class TBlock {

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();
		// No idea about this, but if you change A,B,C or D the maps
		// will not load, you can change the rest without easily visible
		// problems although I suppose they must do something.
		os.write2(0);
		os.write(0); // A
		os.write3(0);
		os.write3(0);
		os.write(0); // B
		os.write2(0);
		os.write(0); // C
		os.write4(0);
		os.write(0); // D
		os.write2(0);
	}
}
