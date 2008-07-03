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
	private long sum;

	public void write(Block block) throws IOException {
		StructuredOutputStream os = block.getOutputStream();
		// If you change A,B,C or D the maps
		// will not load, you can change the rest without easily visible
		// problems although I suppose they must do something.
		//
		// A,B,C,D is a standard crc32 sum of the rest of the file.
		// (Andrzej Popowsk)
		os.write2(0);
		os.write((int) (sum >> 24)); // A
		os.write3(0);
		os.write3(0);
		os.write((int) (sum >> 16)); // B
		os.write2(0);
		os.write((int) (sum >> 8)); // C
		os.write4(0);
		os.write((int) sum); // D
		os.write2(0);
	}

	public void setSum(long sum) {
		this.sum = sum;
	}
}
