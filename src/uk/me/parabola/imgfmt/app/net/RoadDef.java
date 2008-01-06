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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.WriteStrategy;

import java.util.List;

/**
 * A road definition.  This ties together all parts of a single road and provides
 * street address information.
 *
 * @author Steve Ratcliffe
 */
public class RoadDef {
	private static final int MAX_LABELS = 4;

	// There can be up to 4 labels for the same road.
	private Label[] label = new Label[MAX_LABELS];

	private byte roadData;

	private int roadLength;  // in feet?

	private List<RoadIndex> roadIndexes;

	void write(WriteStrategy writer) {
		Label l = label[0];
		l.getOffset();
	}
}
