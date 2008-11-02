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

import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.util.List;
import java.util.ArrayList;

/**
 * The NET file.  This consists of information about roads.  It is not clear
 * what this file brings on its own (without NOD) but may allow some better
 * searching, street addresses etc.
 *
 * @author Steve Ratcliffe
 */
public class NETFile extends ImgFile {
	private final NETHeader netHeader = new NETHeader();

	private final List<RoadDef> roaddefs = new ArrayList<RoadDef>();

	public NETFile(ImgChannel chan, boolean write) {
		setHeader(netHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(NETHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			netHeader.readHeader(getReader());
		}
	}

	public void write() {
		// Write out the actual file body.
		writeBody();
	}

	public void writePost() {
		getHeader().writeHeader(getWriter());
	}

	private void writeBody() {
		ImgFileWriter writer = getWriter();

		int start = writer.position();
		netHeader.startRoadDefs(start);
		for (RoadDef r : roaddefs) {
			r.write(writer, writer.position() - start);
		}
		netHeader.endRoadDefs(position());
	}

	public RoadDef createRoadDef(Label l) {
		RoadDef r = new RoadDef();
		r.addLabel(l);

		roaddefs.add(r);

		return r;
	}

	public void allRoadDefsDone() {
		int ofs = 0;
		for (RoadDef r : roaddefs)
			ofs += r.calcOffset(ofs);
	}
}
