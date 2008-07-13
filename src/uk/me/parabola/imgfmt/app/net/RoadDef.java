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
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.OffsetWriterList;
import uk.me.parabola.imgfmt.app.trergn.Polyline;

import java.util.List;
import java.util.ArrayList;

/**
 * A road definition.  This ties together all segments of a single road
 * and provides street address information.
 *
 * @author Steve Ratcliffe
 */
public class RoadDef {
	private static final int MAX_LABELS = 4;

	private int offset = -1;
	private OffsetWriterList owList = new OffsetWriterList();

	// There can be up to 4 labels for the same road.
	private final Label[] labels = new Label[MAX_LABELS];
	private int numlabels;

	// Speeed and class
	private byte roadFlags = (byte) 0x4;

	// The road length is probably determined by other flags in the header
	private int roadLength;

	private List<RoadIndex> roadIndexes = new ArrayList<RoadIndex>();

	public void addOffsetTarget(ImgFileWriter writer, int ormask) {
		owList.addTarget(writer, ormask);
	}

	public void addPolylineRef(Polyline pl) {
		roadIndexes.add(new RoadIndex(pl));
	}

	private int getMaxZoomLevel() {
		int m = 0;
		for (RoadIndex ri : roadIndexes) {
			int z = ri.getZoomLevel();
			m = (z > m ? z : m);
		}
		return m;
	}

	int calcOffset(int ofs) {
		offset = ofs;
		if (owList != null) {
			owList.writeOffset(ofs);
		}

		int len = 5; // basic len
		len += 3 * numlabels;
		len += getMaxZoomLevel();
		len += 3 * roadIndexes.size();
		return len;
	}

	void write(ImgFileWriter writer, int realofs) {
		assert offset == realofs;
		for (int i = 0; i < numlabels; i++) {
			Label l = labels[i];
			int ptr = l.getOffset();
			if (i == (numlabels-1))
				ptr |= 0x800000;
			writer.put3(ptr);
		}
		writer.put(roadFlags);
		writer.put3(roadLength);

		int maxlevel = getMaxZoomLevel();
		for (int i = 0; i <= maxlevel; i++) {
			byte b = 0;
			for (RoadIndex ri : roadIndexes) {
				if (ri.getZoomLevel() == i)
					b++;
			}
			if (i == maxlevel)
				b |= 0x80;
			writer.put(b);
		}

		for (int i = 0; i <= maxlevel; i++) {
			for (RoadIndex ri : roadIndexes) {
				if (ri.getZoomLevel() == i)
					ri.write(writer);
			}
		}
	}

	public void addLabel(Label l) {
		if (numlabels >= MAX_LABELS)
			throw new IllegalStateException("Too many labels");
		labels[numlabels++] = l;
	}
}
