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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.lbl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;

/**
 * A highway is in a region.
 *
 * @author Mark Burton
 */
public class Highway {

	class ExitPoint implements Comparable<ExitPoint> {
		final String name;
		final byte index;
		final Subdivision div;
		public ExitPoint(String name, byte index, Subdivision div) {
			this.name = name;
			this.index = index;
			this.div = div;
		}

	    public int compareTo(ExitPoint o) {
			return name.compareTo(o.name);
	    }
	}

	private final int index;

	private final Region region;

	private final List<ExitPoint> exits = new ArrayList<ExitPoint>();

	private Label label;

	private int extraDataOffset; // in 3-byte records - 1 based

	public Highway(Region region, int index) {
		this.region = region;
		this.index = index;
	}

	void write(ImgFileWriter writer, boolean extraData) {
		if(extraData) {
			writer.put((byte)0);
			writer.putChar(region == null? 0 : region.getIndex());
			Collections.sort(exits);
			for(ExitPoint ep : exits) {
			    writer.put(ep.index);
			    writer.putChar((char)ep.div.getNumber());
			}
		}
		else {
			assert extraDataOffset != 0;
			writer.put3(label.getOffset());
			writer.putChar((char)extraDataOffset);
			writer.put((byte)0); // unknown (setting any of 0x3f stops exits being found)
		}
	}

	public int getIndex() {
		return index;
	}

	public void setLabel(Label label) {
		this.label = label;
	}

	public void setExtraDataOffset(int extraDataOffset) {
		this.extraDataOffset = extraDataOffset / 3 + 1;
	}

	public int getExtraDataSize() {
		return (1 + exits.size()) * 3;
	}

	public void addExitPoint(String name, int index, Subdivision div) {
		exits.add(new ExitPoint(name, (byte)index, div));
	}
}
