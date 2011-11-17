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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.SectionWriter;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The TYP file.
 *
 * @author Thomas Lußnig
 * @author Steve Ratcliffe
 */
public class TYPFile extends ImgFile {
	private static final Logger log = Logger.getLogger(TYPFile.class);

	private final TYPHeader header = new TYPHeader();

	private TypData data;

	private final Map<Integer, Integer> strToType = new TreeMap<Integer, Integer>();
	private final Map<Integer, Integer> typeToStr = new TreeMap<Integer, Integer>();

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

		writeLabels(writer);
		writeStrIndex(writer);
		writerTypeIndex(writer);

		zapZero(header.getShapeStacking(), header.getLabels(), header.getStringIndex(), header.getTypeIndex());

		log.debug("syncing TYP file");
		position(0);
		getHeader().writeHeader(getWriter());
	}

	private void writeLabels(ImgFileWriter in) {
		if (data.getIcons().isEmpty())
			return;
		
		SectionWriter writer = header.getLabels().makeSectionWriter(in);

		List<SortKey<TypIconSet>> keys = new ArrayList<SortKey<TypIconSet>>();
		Sort sort = data.getSort();
		for (TypIconSet icon : data.getIcons()) {
			String label = icon.getLabel();
			if (label != null) {
				SortKey<TypIconSet> key = sort.createSortKey(icon, label);
				keys.add(key);
			}
		}
		Collections.sort(keys);

		// Offset 0 is reserved to mean no label.
		writer.put((byte) 0);

		for (SortKey<TypIconSet> key : keys) {
			int off = writer.position();
			TypIconSet icon = key.getObject();
			int type = icon.getTypeForFile();

			String label = icon.getLabel();
			if (label != null) {
				CharBuffer cb = CharBuffer.wrap(label);
				CharsetEncoder encoder = data.getEncoder();
				try {
					ByteBuffer buffer = encoder.encode(cb);
					writer.put(buffer);

					// If we succeeded then note offsets for indexes
					strToType.put(off, type);
					typeToStr.put(type, off);

				} catch (CharacterCodingException ignore) {
					String name = encoder.charset().name();
					throw new TypLabelException(name);
				}
				writer.put((byte) 0);
			}
		}
		Utils.closeFile(writer);
	}

	private void writeStrIndex(ImgFileWriter in) {
		SectionWriter writer = header.getStringIndex().makeSectionWriter(in);
		int psize = ptrSize(header.getLabels().getSize());
		header.getStringIndex().setItemSize((char) (3 + psize));

		for (Map.Entry<Integer, Integer> ent : strToType.entrySet()) {
			putN(writer, psize, ent.getKey());
			putN(writer, 3, ent.getValue());
		}
		Utils.closeFile(writer);
	}

	private void writerTypeIndex(ImgFileWriter in) {
		SectionWriter writer = header.getTypeIndex().makeSectionWriter(in);
		int psize = ptrSize(header.getLabels().getSize());
		header.getTypeIndex().setItemSize((char) (3 + psize));

		for (Map.Entry<Integer, Integer> ent : typeToStr.entrySet()) {
			putN(writer, 3, ent.getKey());
			putN(writer, psize, ent.getValue());
		}
		Utils.closeFile(writer);
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
		int psize = ptrSize(size);
		//if (psize == 1)
		//	psize = 2;
		indexSection.setItemSize((char) (typeSize + psize));

		subWriter = indexSection.makeSectionWriter(writer);
		for (TypElement elem : elementData) {
			int offset = elem.getOffset();
			int type = elem.getTypeForFile();
			putN(writer, typeSize, type);
			putN(writer, psize, offset);
		}
		Utils.closeFile(subWriter);

		zapZero(dataSection, indexSection);
	}

	private void zapZero(Section... sect) {
		for (Section s : sect) {
			if (s.getSize() == 0) {
				s.setPosition(0);
				s.setItemSize((char) 0);
			}
		}
	}

	private int ptrSize(int size) {
		int psize = 1;
		if (size > 0xffffff)
			psize = 4;
		else if (size > 0xffff)
			psize = 3;
		else if (size > 0xff)
			psize = 2;
		return psize;
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
