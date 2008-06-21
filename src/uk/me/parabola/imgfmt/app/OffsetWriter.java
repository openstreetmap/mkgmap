/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Author: Elrond
 * Create date: 2008-05-20
 */
package uk.me.parabola.imgfmt.app;


public class OffsetWriter {
	private WriteStrategy writer;
	private int targetOffset;
	private int orMask;

	public OffsetWriter(WriteStrategy w, int ormask) {
		writer = w;
		targetOffset = w.position();
		orMask = ormask;
	}

	public void writeOffset(int ofs) {
		int cur_ofs = writer.position();
		writer.position(targetOffset);
		writer.put3(ofs | orMask);
		writer.position(cur_ofs);
	}
}
