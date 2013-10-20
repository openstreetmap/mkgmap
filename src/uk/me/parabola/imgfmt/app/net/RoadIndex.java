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
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;


/**
 * @author Steve Ratcliffe
 */
public class RoadIndex {
	private final Polyline linkedRoad;
	private final long splitId;
	// int Subdivision.getNumber()

	public RoadIndex(Polyline road, long splitId) {
		linkedRoad = road;
		this.splitId = splitId;
	}

	private Subdivision getSubdiv() {
		return linkedRoad.getSubdiv();
	}

	Polyline getLine() {
		return linkedRoad;
	}

	public long getSplitId() {
		return splitId;
	}

	void write(ImgFileWriter writer) {
		int roadnum = linkedRoad.getNumber();
		assert roadnum < 256;
		writer.put((byte) roadnum);
		char subdivnum = (char) getSubdiv().getNumber();
		writer.putChar(subdivnum);
	}
}
