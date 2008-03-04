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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.tdbfmt;

import uk.me.parabola.log.Logger;
import uk.me.parabola.io.StructuredInputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * A copyright block consists of a number of copyright segments.
 *
 * @author Steve Ratcliffe
 */
class CopyrightBlock {
	private static final Logger log = Logger.getLogger(CopyrightBlock.class);
	
	private final List<CopyrightSegment> segments = new ArrayList<CopyrightSegment>();
	private final Set<CopyrightSegment> copySet = new HashSet<CopyrightSegment>();

	CopyrightBlock() {
	}

	CopyrightBlock(Block block) throws IOException {
		StructuredInputStream ds = block.getInputStream();

		while (!ds.testEof()) {
			CopyrightSegment segment = new CopyrightSegment(ds);

			log.info("segment: " + segment);
			segments.add(segment);
		}
	}

	public void write(Block block) throws IOException {
		for (CopyrightSegment seg : segments) {
			seg.write(block);
		}
	}

	/**
	 * Add a copyright segment.  We only add unique ones.
	 * @param seg The copyright segment to add.
	 */
	public void addSegment(CopyrightSegment seg) {
		if (copySet.add(seg))
			segments.add(seg);
	}
}
