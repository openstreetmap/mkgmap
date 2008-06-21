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

import java.util.List;
import java.util.ArrayList;


public class OffsetWriterList {
	private final List<OffsetWriter> targets = new ArrayList<OffsetWriter>();

	public void addTarget(WriteStrategy writer, int ormask) {
		OffsetWriter ow = new OffsetWriter(writer, ormask);
		targets.add(ow);
	}

	public void writeOffset(int ofs) {
		for (OffsetWriter t : targets) {
			t.writeOffset(ofs);
		}
	}
}
