/*
 * Copyright (C) 2008 mkgmap contributers.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.mdr;

import java.io.IOException;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

/**
 * The MDR file.  This is embedded into a .img file, either its own
 * separate one, or as one file in the gmapsupp.img.
 *
 * @author Steve Ratcliffe
 */
public class MDRFile extends ImgFile {

	public MDRFile(ImgChannel chan, boolean write) {
		MDRHeader header = new MDRHeader();
		setHeader(header);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));

			// Position at the start of the writable area.
			position(header.getHeaderLength());
		} else {
			setReader(new BufferedImgFileReader(chan));
			header.readHeader(getReader());
		}
	}

	private void write() {
		// Now refresh the header
		position(0);
		getHeader().writeHeader(getWriter());
	}

	/**
	 * A test program to create a simple mdr file for testing that
	 * something valid is being produced.
	 */
	public static void main(String[] args) throws IOException {
		// Create the .img filesystem/archive
		FileSystemParam params = new FileSystemParam();
		FileSystem fs = ImgFS.createFs("test_mdr.img", params);

		// Create the MDR file within the .img
		ImgChannel chan = fs.create("FRED.MDR");

		try {
			// Wrap the MDR channel with the MDRFile
			MDRFile file = new MDRFile(chan, true);
			file.write();
			file.close();
		} finally {
			chan.close();
			fs.close();
		}
	}
}
