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

import java.nio.charset.CharsetEncoder;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.SectionWriter;
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

	private TypData data;

	public TYPFile(ImgChannel chan) {
		setHeader(header);
		setWriter(new BufferedImgFileWriter(chan));
		position(TYPHeader.HEADER_LEN);
	}

	public void write() {
		// HEADER_LEN => 1. Image
		//Collections.sort(images, BitmapImage.comparator());
		// TODO we will probably have to sort something.

		ImgFileWriter writer = getWriter();
		writer.position(TYPHeader.HEADER_LEN);

		writeSection(writer, header.getPolygonData(), header.getPolygonIndex(), data.getPolygons());
		writeSection(writer, header.getLineData(), header.getLineIndex(), data.getLines());
		writeSection(writer, header.getPointData(), header.getPointIndex(), data.getPoints());

		SectionWriter subWriter = header.getShapeStacking().makeSectionWriter(writer);
		data.getStacking().write(subWriter);
		Utils.closeFile(subWriter);

		writeSection(writer, header.getIconData(), header.getIconIndex(), data.getIcons());

		log.debug("syncing TYP file");
		position(0);
		getHeader().writeHeader(getWriter());
	}

	private void writeSection(ImgFileWriter writer, Section dataSection, Section indexSection,
			List<? extends TypElement> elementData)
	{
		SectionWriter subWriter = dataSection.makeSectionWriter(writer);
		CharsetEncoder encoder = data.getEncoder();
		for (TypElement elem : elementData)
			elem.write(subWriter, encoder);
		Utils.closeFile(subWriter);

		int size = dataSection.getSize();
		int typeSize = indexSection.getItemSize();
		int psize = 1;
		if (size > 0xffffff)
			psize = 4;
		else if (size > 0xffff)
			psize = 3;
		else if (size > 0xff)
			psize = 2;
		indexSection.setItemSize((char) (typeSize + psize));

		subWriter = indexSection.makeSectionWriter(writer);
		for (TypElement elem : elementData) {
			int offset = elem.getOffset();
			int type = (elem.getType() << 5) | (elem.getSubType() & 0x1f);
			putN(writer, typeSize, type);
			putN(writer, psize, offset);
		}
		Utils.closeFile(subWriter);
	}

	protected void putN(ImgFileWriter writer, int n, int value) {
		switch (n) {
		case 1:
			writer.put((byte) value);
			break;
		case 2:
			writer.putChar((char) value);
			break;
		case 3:
			writer.put3(value);
			break;
		case 4:
			writer.putInt(value);
			break;
		default: // Don't write anything.
			assert false;
			break;
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
