/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.mdr;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * The header of the MDR file.
 *
 * Note that there are many possible sections in this file and that
 * only a certain number of them are needed.  There are also many
 * different lengths for the record sizes of the sections. Finally
 * there are different sections and record sizes for the version
 * that gets loaded into the gmapsupp.
 *
 * @author Steve Ratcliffe
 */
public class MDRHeader extends CommonHeader {

	private static final int MAX_SECTIONS = 19;
	private final Section[] sections = new Section[MAX_SECTIONS+1];

	// The section lengths that we are going to implement to begin
	// with.  These lengths are not at all constant so this array will
	// eventually not be needed. Consider them defaults.
	private final char[] sectRecLen = {
		0,
			8, 2, 2, 3,  // 1 - 4
			11, 6, 7, 6,  // 5 - 8
			4, 0, 12, 7,  // 9 - 12
			8, 6, 0, 0,  // 13 - 16
			0, 0, 0, 0,  // 17 - 20
	};

	public MDRHeader(int headerLen) {
		super(headerLen, "GARMIN MDR");

		// Do a quick initialisation.  Link every section to the
		// previous one so that all the positions are correct.
		for (int i = 1; i < sections.length; i++) {
			Section prev = (i == 0) ? null : sections[i - 1];
			sections[i] = new Section(prev, sectRecLen[i]);
		}
		sections[1].setPosition(getHeaderLength());
	}

	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		throw new UnsupportedOperationException("not implemented yet");
	}

	/**
	 * Write out the application header.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar((char) 1252); // XXX code page
		writer.putChar((char) 1);
		writer.putChar((char) 1);
		writer.putChar((char) 14);

		sections[1].writeSectionInfo(writer);
		writer.putInt(1);

		sections[2].writeSectionInfo(writer);
		writer.putInt(0);

		sections[3].writeSectionInfo(writer);
		writer.putInt(0);

		sections[4].writeSectionInfo(writer);
		writer.putInt(0);

		sections[5].writeSectionInfo(writer);
		writer.putInt(0x1d);

		sections[6].writeSectionInfo(writer);
		writer.putInt(2);

		sections[7].writeSectionInfo(writer);
		writer.putInt(3);

		sections[8].writeSectionInfo(writer);
		writer.putInt(0x40a);

		sections[9].writeSectionInfo(writer);
		writer.putInt(0);

		sections[10].writeSectionInfo(writer);
		writer.putInt(0);

		sections[11].writeSectionInfo(writer);
		writer.putInt(19);

		sections[12].writeSectionInfo(writer);
		writer.putInt(0x40a);

		sections[13].writeSectionInfo(writer);
		writer.putInt(0);

		sections[14].writeSectionInfo(writer);
		writer.putInt(0);

		sections[15].writeSectionInfo(writer);
		writer.put((byte) 0);

		sections[16].writeSectionInfo(writer);
		writer.putChar((char) 0);
		writer.putInt(0);

		sections[17].writeSectionInfo(writer);
		writer.putInt(0);

		sections[18].writeSectionInfo(writer);
		writer.putChar((char) 0);
		writer.putInt(0);

		sections[19].writeSectionInfo(writer);
		writer.putChar((char) 0);
		writer.putInt(0);
	}

	public void setSectSize(int sectionNumber, int size, int itemSize) {
		Section section = sections[sectionNumber];
		section.setSize(size);
		section.setItemSize((char) itemSize);
	}

	public void setSectSize(int sectionNumber, int size) {
		setSectSize(sectionNumber, size, 0);
	}

	public void setPosition(int sectionNumber, int position) {
		sections[sectionNumber].setPosition(position);
	}
}
