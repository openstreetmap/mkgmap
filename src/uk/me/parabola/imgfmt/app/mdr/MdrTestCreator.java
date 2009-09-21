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

import java.io.IOException;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

/**
 * @author Steve Ratcliffe
 */
public class MdrTestCreator {

	private void create() throws IOException {
		// Create the .img file system/archive
		FileSystemParam params = new FileSystemParam();
		FileSystem fs = ImgFS.createFs("test_mdr.img", params);

		// Create the MDR file within the .img
		ImgChannel chan = fs.create("FRED.MDR");

		try {
			// Wrap the MDR channel with the MDRFile
			MdrConfig config = new MdrConfig();
			config.setHeaderLen(286);
			config.setWritable(true);
			config.setForDevice(false);
			MDRFile file = new MDRFile(chan, config);
			populate(file);
			file.write();
			file.close();
		} finally {
			chan.close();
			fs.close();
		}
	}

	private void populate(MDRFile file) {
		file.addMap(63240001);
	}

	/**
	 * A test program to create a simple mdr file for testing that
	 * something valid is being produced.
	 */
	public static void main(String[] args) throws IOException {
		MdrTestCreator t = new MdrTestCreator();
		t.create();
	}
}
