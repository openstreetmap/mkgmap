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

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
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
public class MDRFile extends ImgFile {

	private final MDRHeader mdrHeader;

	// The sections
	private final Mdr1 mdr1;

	public MDRFile(ImgChannel chan, MdrConfig config) {
		mdrHeader = new MDRHeader(config.getHeaderLen());
		setHeader(mdrHeader);
		if (config.isWritable()) {
			setWriter(new BufferedImgFileWriter(chan));

			// Position at the start of the writable area.
			position(mdrHeader.getHeaderLength());
		} else {
			setReader(new BufferedImgFileReader(chan));
			mdrHeader.readHeader(getReader());
		}

		mdr1 = new Mdr1(config);
	}

	public void addMap(int mapNumber) {
		mdr1.addMap(mapNumber);
	}

	public void write() {
		writeSections();

		// Now refresh the header
		position(0);
		getHeader().writeHeader(getWriter());
	}

	private void writeSections() {
		ImgFileWriter writer = getWriter();

		int size = mdr1.writeSectData(writer);
		int recSize = mdr1.getRecSize();
		mdrHeader.setSectSize(1, size);
	}

}
