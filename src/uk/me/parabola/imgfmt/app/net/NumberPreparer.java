/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.List;

import uk.me.parabola.imgfmt.app.BitWriter;

/**
 * Class to prepare the bit stream of the house numbering information.
 *
 * @author Steve Ratcliffe
 */
public class NumberPreparer {
	private final List<Numbering> numbers;

	public NumberPreparer(List<Numbering> numbers) {
		this.numbers = numbers;
	}

	public BitWriter makeBitStream() {
		// Write the bitstream
		BitWriter bw = new BitWriter();


		return bw;
	}
}
