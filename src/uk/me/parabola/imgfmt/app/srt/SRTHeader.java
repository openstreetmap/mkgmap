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
	public static final int HEADER_LEN = 29; // newer files use 37
	protected static final int HEADER2_LEN = 16;
	protected static final int HEADER3_LEN = 52;
	protected static final int HEADER3_MULTI_LEN = 92;

	// The section structure of this file is somewhat different to other
	// files, but I am still going to model it using Section.
	private final Section header2 = new Section();

	private final Section desc = new Section(header2);
	private final Section subheader = new Section(desc);
	private final Section chartab = new Section((char) 3);
	private final Section expansions = new Section(chartab, (char) 2);
	private final Section srt8 = new Section(expansions, (char) 5);
	private final Section srt7 = new Section(srt8, (char) 4);
	

	private Sort sort;

	public SRTHeader() {
		this(HEADER_LEN);
	}

	public SRTHeader(int headerLen) {
		super(headerLen, "GARMIN SRT");
		header2.setPosition(headerLen); 
		header2.setSize(16);

		chartab.setPosition(HEADER3_LEN);
	}

	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		reader.getChar(); // expected: 1
		header2.setPosition(reader.getInt());
		header2.setSize(reader.getChar());
	}

	/**
	 * Write out the application header.  This is unusual as it just points
	 * to an area which is itself just a header.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		if (getHeaderLength() <= 29) {
			writer.putChar((char) 1);

			writer.putInt(header2.getPosition());
			writer.putChar((char) header2.getSize());
		} else {
			// this appears in unicode maps dated 2016 and 2017
			writer.putChar((char) 0);
			writer.putInt(header2.getPosition());
			writer.putChar((char) header2.getSize());
			writer.putChar((char) 1);
			writer.putInt(header2.getPosition());
			writer.putChar((char) header2.getSize());
			
			// older maps contain this variant (note the different order)
//			writer.putChar((char) 1);
//			writer.putInt(header2.getPosition());
//			writer.putChar((char) header2.getSize());
//			writer.putChar((char) 0);
//			writer.putInt(desc.getPosition()); 
//			writer.putChar((char) 0x10);  
			
		}
	}

	/**
	 * Section writer to write the character and expansions sections. These two sections are embedded within another
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
		if (sort.isMulti()) {
			writer.putChar((char) HEADER3_MULTI_LEN);
		} else {
			writer.putChar((char) HEADER3_LEN);
		}

		writer.putChar((char) sort.getId1());
		writer.putChar((char) sort.getId2());
		writer.putChar((char) sort.getCodepage());
		if (sort.isMulti())
			writer.putInt(0x6f02);
		else
			writer.putInt(0x2002);

		chartab.writeSectionInfo(writer, true, true);
		writer.putChar((char) 0);

		expansions.writeSectionInfo(writer, true, true);
		writer.putChar((char) 0);

		// SRT6 A repeat pointer to the single byte character table
		writer.putInt(chartab.getPosition());
		writer.putInt(0);

		if (sort.isMulti()) {
			writer.putInt(1);
			writer.putInt(sort.getMaxPage());  // max block in srt7

			srt7.writeSectionInfo(writer, true);
			writer.putChar((char) 0);
			writer.putInt(0);
			srt8.writeSectionInfo(writer, true);

			writer.putChar((char) 0);
			writer.putInt(0);
		}
	}

	public void setSort(Sort sort) {
		this.sort = sort;
		if (sort.isMulti()) {
			chartab.setPosition(HEADER3_MULTI_LEN);
			chartab.setItemSize((char) 5);
			expansions.setItemSize((char) 4);
		}
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

	/** Called after the expansions has been written to record the position. */
	public void endTab2(int postition) {
		subheader.setSize(postition - subheader.getPosition());
		expansions.setSize(postition - expansions.getPosition());
	}

	public void endSrt8(int position) {
		srt8.setSize(position - srt8.getPosition());
	}

	public void endSrt7(int position) {
		srt7.setSize(position - srt7.getPosition());
	}
}
