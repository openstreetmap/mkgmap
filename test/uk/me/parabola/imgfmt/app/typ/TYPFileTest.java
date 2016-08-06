/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 01-Dec-2008
 */
package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

import org.junit.Test;

import func.lib.TestUtils;

import static org.junit.Assert.*;

public class TYPFileTest {
	
	@Test
	public void testWrite() throws Exception {
		TestUtils.registerFile("test.typ");
		FileSystemParam params = new FileSystemParam();
		try (FileSystem fs = ImgFS.createFs("test.typ", params)) {
			ImgChannel channel = fs.create("XXX.TYP");
			TYPFile typFile = new TYPFile(channel);
			assertNotNull("typ file is created", typFile);
		}
	}
}
