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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
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
 * So we are writing NOD first before NET and NOD1 before NOD2.  Once NET is
 * written then go back to Table A and fix the label offsets in RGN.
 * 
 * @author Steve Ratcliffe
 */
public class NODFile extends ImgFile {
	private static final Logger log = Logger.getLogger(NODFile.class);

	private final NODHeader nodHeader = new NODHeader();

	private List<RouteCenter> centers = new ArrayList<RouteCenter>();
	private List<RoadDef> roads = new ArrayList<RoadDef>();
	private List<RouteNode> boundary = new ArrayList<RouteNode>();

	public NODFile(ImgChannel chan, boolean write) {
		setHeader(nodHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(NODHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			nodHeader.readHeader(getReader());
		}
	}

	public void write() {
		writeNodes();
		writeRoadData();
		writeBoundary();
	}

	public void writePost() {
		ImgFileWriter writer = new SectionWriter(getWriter(), nodHeader.getNodeSection());

		for (RouteCenter rc : centers) {
			rc.writePost(writer);
		}
		// Refresh the header
		position(0);
		getHeader().writeHeader(getWriter());
	}

	/**
	 * Write the nodes (NOD 1).  This is done first as the offsets into
	 * this section are needed to write NOD2.
	 */
	private void writeNodes() {
		ImgFileWriter writer = getWriter();
		nodHeader.setNodeStart(writer.position());

		Section section = nodHeader.getNodeSection();
		writer = new SectionWriter(writer, section);

		for (RouteCenter cp : centers)
			cp.write(writer);
		nodHeader.setNodeSize(writer.position());
		log.debug("the nod offset", Integer.toHexString(getWriter().position()));
		Section.close(writer);
	}

	/**
	 * Write the road data (NOD2).
	 */
	private void writeRoadData() {
		log.info("writeRoadData");

		ImgFileWriter writer = new SectionWriter(getWriter(), nodHeader.getRoadSection());

		boolean debug = log.isDebugEnabled();
		for (RoadDef rd : roads) {
			if(debug)
				log.debug("wrting nod2", writer.position());
			rd.writeNod2(writer);
		}
		if(debug)
			log.debug("ending nod2", writer.position());
		nodHeader.setRoadSize(writer.position());
	}

	/**
	 * Write the boundary node table (NOD3).
	 */
	private void writeBoundary() {
		log.info("writeBoundary");

		Collections.sort(boundary);

		ImgFileWriter writer = new SectionWriter(getWriter(), nodHeader.getBoundarySection());

		boolean debug = log.isDebugEnabled();
		for (RouteNode node : boundary) {
			if(debug)
				log.debug("wrting nod3", writer.position());
			node.writeNod3(writer);
		}
		if(debug)
			log.debug("ending nod3", writer.position());
		nodHeader.setBoundarySize(writer.position());
	}

	public void setNetwork(List<RouteCenter> centers, List<RoadDef> roads, List<RouteNode> boundary) {
		this.centers = centers;
		this.roads = roads;
		this.boundary = boundary;
	}
}
