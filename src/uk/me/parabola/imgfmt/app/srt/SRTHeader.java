/*
 * Copyright (C) 2010.
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
package uk.me.parabola.imgfmt.app.srt;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;
import uk.me.parabola.imgfmt.app.SectionWriter;

/**
 * The header of the SRT file.
 *
 * This file determines the sort order of the label characters.
 *
 * @author Steve Ratcliffe
 */
public class SRTHeader extends CommonHeader {
	// The header length we are using for the SRT file
	private static final int HEADER_LEN = 29;
	protected static final int HEADER2_LEN = 16;
	protected static final int HEADER3_LEN = 52;

	// The section structure of this file is somewhat different to other
	// files, but I am still going to model it using Section.
	private final Section header = new Section();
	//private final Section pointers = new Section(header);
	private final Section desc = new Section(header);
	private final Section subheader = new Section(desc);
	private final Section chartab = new Section((char) 3);
	private final Section tab2 = new Section(chartab, (char) 2);

	private char codepage = (char) 1252;

	public SRTHeader() {
		super(HEADER_LEN, "GARMIN SRT");
		header.setPosition(HEADER_LEN);
		header.setSize(16);

		chartab.setPosition(HEADER3_LEN);
	}

	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		throw new UnsupportedOperationException("not implemented yet");
	}

	/**
	 * Write out the application header.  This is unusual as it just points
	 * to an area which is itself just a header.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar((char) 1);

		writer.putInt(header.getPosition());
		writer.putChar((char) header.getSize());

		//writeHeader2(writer);

		//writeDescription(writer);
		//writeHeader3(writer);
	}

	/**
	 * Section writer to write the character and tab2 sections. These two sections are embedded within another
	 * section and their offsets are relative to that section.
	 * @param writer The real underlying writer.
	 * @return A new writer where offsets are relative to the start of the sub-header section.
	 */
	SectionWriter makeSectionWriter(ImgFileWriter writer) {
		return new SectionWriter(writer, subheader);
	}

	/**
	 * This is a header with pointers to the description and the main section.
	 * The offsets contained in this section are relative to the beginning of the file.
	 * @param writer Header is written here.
	 */
	protected void writeHeader2(ImgFileWriter writer) {
		desc.writeSectionInfo(writer);
		subheader.writeSectionInfo(writer);
	}

	/**
	 * Header contained within the main section.  Offsets within this section are relative to the beginning
	 * of the section.
	 * @param writer Header is written here.
	 */
	protected void writeHeader3(ImgFileWriter writer) {
		writer.putChar((char) HEADER3_LEN);
		writer.putChar((char) 7);
		writer.putChar((char) 2);
		writer.putChar(codepage);
		writer.putInt(0x2002);

		chartab.writeSectionInfo(writer, true, true);

		writer.putChar((char) 0);
		tab2.writeSectionInfo(writer, true, true);

		writer.putChar((char) 0);
		writer.putInt(0x34);
		writer.putInt(0);
	}

	public void setCodepage(char codepage) {
		this.codepage = codepage;
	}

	/** Called after the description has been written to record the position. */
	public void endDescription(int position) {
		desc.setSize(position - desc.getPosition());
		subheader.setPosition(position);
	}

	/** Called after the character table has been written to record the position. */
	public void endCharTable(int position) {
		chartab.setSize(position - chartab.getPosition());
	}

	/** Called after the tab2 has been written to record the position. */
	public void endTab2(int postition) {
		subheader.setSize(postition - subheader.getPosition());
		tab2.setSize(postition - tab2.getPosition());
	}
}
