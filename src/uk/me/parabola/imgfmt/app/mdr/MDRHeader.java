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

	private static final int MAX_SECTIONS = 40;
	private final Section[] sections = new Section[MAX_SECTIONS+1];

	private int codepage = 1252;

	public MDRHeader(int headerLen) {
		super(headerLen, "GARMIN MDR");

		// Do a quick initialisation.  Link every section to the
		// previous one so that all the positions are correct.
		for (int i = 1; i < sections.length; i++) {
			Section prev = (i == 0) ? null : sections[i - 1];
			sections[i] = new Section(prev);
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
		writer.putChar((char) codepage);
		writer.putChar((char) 7); // TODO from srt
		writer.putChar((char) 2); // TODO from srt
		writer.putChar((char) 14);

		sections[1].writeSectionInfo(writer, true, true);
		sections[2].writeSectionInfo(writer, true, true);
		sections[3].writeSectionInfo(writer, true, true);
		sections[4].writeSectionInfo(writer, true, true);
		sections[5].writeSectionInfo(writer, true, true);
		sections[6].writeSectionInfo(writer, true, true);
		sections[7].writeSectionInfo(writer, true, true);
		sections[8].writeSectionInfo(writer, true, true);
		sections[9].writeSectionInfo(writer, true, true);
		sections[10].writeSectionInfo(writer, false, true);
		sections[11].writeSectionInfo(writer, true, true);
		sections[12].writeSectionInfo(writer, true, true);
		sections[13].writeSectionInfo(writer, true, true);
		sections[14].writeSectionInfo(writer, true, true);
		sections[15].writeSectionInfo(writer);
		writer.put((byte) 0);

		sections[16].writeSectionInfo(writer, true, true);
		sections[17].writeSectionInfo(writer, false, true);
		sections[18].writeSectionInfo(writer, true, true);
		sections[19].writeSectionInfo(writer, true, true);
		sections[20].writeSectionInfo(writer, true, true);
		sections[21].writeSectionInfo(writer, true, true);
		sections[22].writeSectionInfo(writer, true, true);
	}

	public void setItemSize(int sectionNumber, int itemSize) {
		Section section = sections[sectionNumber];
		section.setItemSize((char) itemSize);
	}

	public void setExtraValue(int sectionNumber, int extraValue) {
		Section section = sections[sectionNumber];
		section.setExtraValue(extraValue);
	}
	
	public void setPosition(int sectionNumber, int position) {
		sections[sectionNumber].setPosition(position);
	}

	public void setEnd(int sectionNumber, int position) {
		Section s = sections[sectionNumber];
		s.setSize(position - s.getPosition());
	}

	public void setCodepage(int codepage) {
		this.codepage = codepage;
	}
}
