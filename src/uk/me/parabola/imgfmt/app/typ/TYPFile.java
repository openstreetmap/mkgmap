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
 * Author: Steve Ratcliffe
 * Create date: 03-Dec-2006
 * Change: Thomas Lußnig <gps@suche.org>
 */
package uk.me.parabola.imgfmt.app.typ;

import java.util.LinkedList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.SectionWriter;
import uk.me.parabola.imgfmt.app.Writeable;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The TYP file.
 *
 * @author Thomas Lußnig
 */
public class TYPFile extends ImgFile {
	private static final Logger log = Logger.getLogger(TYPFile.class);

	private final TYPHeader header = new TYPHeader();

	private final List<BitmapImage> images = new LinkedList<BitmapImage>();
	private final List<PointInfo> pointInfo = new LinkedList<PointInfo>();

	private TypData data;

	public TYPFile(ImgChannel chan) {
		setHeader(header);
		setWriter(new BufferedImgFileWriter(chan));
		position(TYPHeader.HEADER_LEN);
	}

	public void setCodePage(int code) {
		header.setCodePage((char) code);
	}

	public void setFamilyId(int code) {
		header.setFamilyId((char) code);
	}

	public void setProductId(int code) {
		header.setProductId((char) code);
	}

	public void write() {
		// HEADER_LEN => 1. Image
		//Collections.sort(images, BitmapImage.comperator());
		// TODO we will probably have to sort somthing.

		ImgFileWriter writer = getWriter();
		writer.position(TYPHeader.HEADER_LEN);

		int pos = writer.position();
		header.getPointData().setPosition(pos);

		for (Writeable w : images)
			w.write(writer);
		int len = writer.position() - pos;
		header.getPointData().setSize(len);

		if (len < 0x100)
			header.getPointIndex().setItemSize((char) 3);
		pos = writer.position();
		for (PointInfo w : pointInfo)
			w.write(writer, header.getPointData().getSize());
		header.getPointIndex().setSize(writer.position() - pos);

		writePolygons(writer);
		
		log.debug("syncing TYP file");
		position(0);
		getHeader().writeHeader(getWriter());
	}

	/**
	 * Write out all the polygon sections.
	 */
	private void writePolygons(ImgFileWriter writer) {
		SectionWriter subWriter = header.getShapeStacking().makeSectionWriter(writer);
		try {
			data.getStacking().write(subWriter);
		} finally {
			Utils.closeFile(subWriter);
		}

		Section polygonData = header.getPolygonData();
		subWriter = polygonData.makeSectionWriter(writer);
		try {
			for (TypPolygon poly : data.getPolygons())
				poly.write(subWriter, data.getEncoder());

		} finally {
			Utils.closeFile(subWriter);
		}

		Section polygonIndex = header.getPolygonIndex();
		subWriter = polygonIndex.makeSectionWriter(writer);
		try {
			for (TypPolygon poly : data.getPolygons()) {
				int offset = poly.getOffset();
				int type = (poly.getType() << 5) | (poly.getSubType() & 0x1f);
				subWriter.putChar((char) type);
				subWriter.putChar((char) offset);
			}
		} finally {
			Utils.closeFile(subWriter);
		}
	}

	public void setData(TypData data) {
		this.data = data;
		TypParam param = data.getParam();
		header.setCodePage((char) param.getCodePage());
		header.setFamilyId((char) param.getFamilyId());
		header.setProductId((char) param.getProductId());
	}
}
