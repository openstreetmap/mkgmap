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
 * Create date: 06-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.SectionWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The NOD file that contains routing information.
 *
 * NOD1 contains several groups of routing nodes.
 * NOD2 contains road data with links into NOD1.
 *
 * NOD1 contains links back to NET (and NET contains links to NOD2).  So there
 * is a loop and we have to write one section first, retaining the offsets
 * and then go back and fill in offsets that were found later.
 *
 * I'm choosing to this with Table A, as the records are fixed size and so
 * we can write them blank the first time and then go back and fix them
 * up, once the NET offsets are known.
 *
 * So we are writing NOD first before NET and NOD1 before NOD2.
 * 
 * @author Steve Ratcliffe
 */
public class NODFile extends ImgFile {
	private static final Logger log = Logger.getLogger(NODFile.class);

	private NODHeader nodHeader = new NODHeader();

	private List<RouteCenter> centers = new ArrayList<RouteCenter>();
	private List<RoadDef> roads = new ArrayList<RoadDef>();

	public NODFile(ImgChannel chan, boolean write) {
		setHeader(nodHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(NODHeader.HEADER_LEN);
			tmpSetup();
		} else {
			setReader(new BufferedImgFileReader(chan));
			nodHeader.readHeader(getReader());
		}
	}

	private void tmpSetup() {
		// XXX hardcoded setup
		RouteCenter rc = new RouteCenter(new Coord(51.2, 0.8));
		centers.add(rc);
		RouteNode node = new RouteNode();
		rc.addNode(node);
	}

	protected void sync() throws IOException {
		if (!isWritable())
			return;

		// Do anything that is in structures and that needs to be dealt with.
		writeBody();

		// Now refresh the header
		position(0);
		getHeader().writeHeader(getWriter());

		getWriter().sync();
	}

	private void writeBody() {
		writeNodes();
		writeRoadData();
	}

	/**
	 * Write the road data NOD2.
	 */
	private void writeRoadData() {
		ImgFileWriter writer = getWriter();
		int start = writer.position();
		nodHeader.setRoadStart(start);

		nodHeader.setRoadSize(writer.position() - start);
	}

	/**
	 * Write the nodes (NOD 1).
	 */
	private void writeNodes() {
		ImgFileWriter writer = getWriter();
		int start = writer.position();
		nodHeader.setNodeStart(start);

		writer = new SectionWriter(getWriter(), start);
		for (RouteCenter cp : centers)
			cp.write(writer);
		nodHeader.setNodeSize(writer.position());
		log.debug("the nod offset", Integer.toHexString(getWriter().position()));
	}

}
