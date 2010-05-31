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

import java.nio.charset.Charset;

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
public class SRTHeader extends CommonHeader {
	// The header length we are using for the SRT file
	private static final int HEADER_LEN = 37;

	// The section structure of this file is somewhat different to other
	// files, but I am still going to model it using Section.
	private final Section[] sections = new Section[5];

	private String description = "German Sort";
	private char codepage = (char) 1252;

	public SRTHeader(int headerLen) {
		super(headerLen, "GARMIN SRT");

		Section last = null;
		for (int i = 0; i < sections.length; i++) {
			Section s = new Section(last);
			sections[i] = s;
			last = s;
		}

		Section s = sections[0];
		s.setPosition(SRTHeader.HEADER_LEN);
		s.setSize(16);
		s = sections[1];
		s.setSize(16);
	}

	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
		throw new UnsupportedOperationException("not implemented yet");
	}

	public void positionForBody() {
	}
	
	/**
	 * Write out the application header.  This is unusual as it just points
	 * to an area which is itself just a header.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar((char) 1);
		Section srt1 = sections[0];
		writer.putInt(srt1.getPosition());
		writer.putChar((char) srt1.getSize());
		writer.putChar((char) 0);

		Section srt2 = sections[1];
		writer.putInt(srt2.getPosition());
		writer.putChar((char) srt2.getSize());
	}

	void writePostHeader(ImgFileWriter writer) {
		writePointers(writer);
		writeDescription(writer);
		writeTableHeader(writer);
	}


	private void writePointers(ImgFileWriter writer) {
	}

	private void writeDescription(ImgFileWriter writer) {
		writer.put(description.getBytes(Charset.forName("ascii")));
		writer.put((byte) 0);
	}

	private void writeTableHeader(ImgFileWriter writer) {
		writer.putChar(codepage);
		writer.putChar((char) 1);
		writer.putChar((char) 1);
		writer.putChar((char) 14);
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setCodepage(char codepage) {
		this.codepage = codepage;
	}
}