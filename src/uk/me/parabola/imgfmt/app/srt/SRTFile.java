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
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.SectionWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The SRT file. This contains a table showing the sort order of
 * the characters that is being used.
 *
 * @author Steve Ratcliffe
 */
public class SRTFile extends ImgFile {

	private SRTHeader header;

	private Sort sort;
	private boolean isMulti;
	
	private String description;

	private final List<Integer> srt8Starts = new ArrayList<>();

	public SRTFile(ImgChannel chan) {
		BufferedImgFileWriter fileWriter = new BufferedImgFileWriter(chan);
		fileWriter.setMaxSize(Long.MAX_VALUE);
		setWriter(fileWriter);
	}

	/**
	 * Write out the file.
	 * This file has an unusual layout. There are several header like structures within
	 * the main body of the file, with the real header being very small.
	 */
	public void write() {
		ImgFileWriter writer = getWriter();
		writeDescription(writer);
		// Position at the start of the writable area.
		position(header.getHeaderLength());
		SectionWriter subWriter = header.makeSectionWriter(writer);
		int header3Len = sort.getHeader3Len();
		if (header3Len == 0)
			header3Len = sort.isMulti()? SRTHeader.HEADER3_MULTI_LEN: SRTHeader.HEADER3_LEN;
		subWriter.position(header3Len);
		writeSrt4Chars(subWriter);
		writeSrt5Expansions(subWriter);
		if (sort.isMulti()) {
			for (int i = 0; i <= sort.getMaxPage(); i++)
				srt8Starts.add(-1);
			writeSrt8(subWriter);
			writeSrt7(subWriter);
		}
		subWriter.close();

		// Header 2 is just after the real header
		writer.position(header.getHeaderLength());
		header.writeHeader2(writer);

		// Header 3 is after the description
		writer.position(header.getHeaderLength() + description.length() + 1 + SRTHeader.HEADER2_LEN);
		header.writeHeader3(writer);

		header.writeHeader(writer);
	}

	private void writeDescription(ImgFileWriter writer) {
		writer.position(header.getHeaderLength() + SRTHeader.HEADER2_LEN);
		writer.put(description.getBytes(Charset.forName("ascii")));
		writer.put((byte) 0);
		header.endDescription(writer.position());
	}

	/**
	 * Write SRT4 the character table.
	 *
	 * @param writer The img file writer.
	 */
	private void writeSrt4Chars(ImgFileWriter writer) {
		for (int i = 1; i < 256; i++) {
			writer.put(sort.getFlags(i));
			writeWeights(writer, i);
		}
		header.endCharTable(writer.position());
	}

	private void writeWeights(ImgFileWriter writer, int i) {
		int primary = sort.getPrimary(i);
		int secondary = sort.getSecondary(i);
		int tertiary = sort.getTertiary(i);
		if (isMulti) {
			assert primary <= 0xffff;
			assert secondary <= 0xff;
			assert tertiary <= 0xff;
			writer.putChar((char) primary);
			writer.put((byte) secondary);
			writer.put((byte) tertiary);
		} else {
			assert primary <= 0xff;
			assert secondary <= 0xf;
			assert tertiary <= 0xf;
			writer.put((byte) primary);
			writer.put((byte) ((tertiary << 4) | (secondary & 0xf)));
		}
	}

	/**
	 * Write SRT5, the expansion table.
	 *
	 * Write out the expansion table. This is referenced from the character table, when
	 * the top nibble of the type is set via the primary position value.
	 */
	private void writeSrt5Expansions(ImgFileWriter writer) {

		int size = sort.getExpansionSize();
		for (int j = 1; j <= size; j++) {
			CodePosition b = sort.getExpansion(j);
			if (isMulti) {
				writer.putChar(b.getPrimary());
				writer.put(b.getSecondary());
				writer.put(b.getTertiary());
			} else {
				writer.put((byte) b.getPrimary());
				writer.put((byte) ((b.getTertiary() << 4) | (b.getSecondary() & 0xf)));
			}
		}

		header.endTab2(writer.position());
	}

	private void writeSrt7(SectionWriter writer) {
		assert sort.isMulti();
		for (int i = 1; i <= sort.getMaxPage(); i++) {
			writer.putInt(srt8Starts.get(i));
		}
		header.endSrt7(writer.position());
	}

	private void writeSrt8(SectionWriter writer) {
		assert sort.isMulti();

		int offset = 0;
		for (int p = 1; p <= sort.getMaxPage(); p++) {
			if (sort.hasPage(p)) {
				srt8Starts.set(p, offset);
				for (int j = 0; j < 256; j++) {
					int ch = p * 256 + j;
					writer.put(sort.getFlags(ch));
					writeWeights(writer, ch);
					offset += 5;
				}
			}
		}
		header.endSrt8(writer.position());
	}

	public void setSort(Sort sort) {
		this.sort = sort;
		header = new SRTHeader(sort.getHeaderLen());
		header.setSort(sort);
		description = sort.getDescription();
		isMulti = sort.isMulti();
	}
}
