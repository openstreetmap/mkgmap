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

import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The MDR file.  This is embedded into a .img file, either its own
 * separate one, or as one file in the gmapsupp.img.
 *
 * @author Steve Ratcliffe
 */
public class SRTFile extends ImgFile {

	private final SRTHeader header;

	public SRTFile(ImgChannel chan) {
		header = new SRTHeader(29);
		setHeader(header);

		BufferedImgFileWriter fileWriter = new BufferedImgFileWriter(chan);
		fileWriter.setMaxSize(Long.MAX_VALUE);
		setWriter(fileWriter);

		// Position at the start of the writable area.
		position(header.getHeaderLength());
	}

	public void write() {
		ImgFileWriter writer = getWriter();
		writeCharacterTable(writer);
		header.writeHeader(writer);
	}

	private void writeCharacterTable(ImgFileWriter writer) {
	}
}