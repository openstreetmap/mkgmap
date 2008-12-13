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

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;

import static org.junit.Assert.*;
import org.junit.Test;

public class TYPFileTest {
	@Test
	public void testWrite() throws FileNotWritableException, FileExistsException {
		FileSystemParam params = new FileSystemParam();
		FileSystem fs = ImgFS.createFs("test.typ", params);
		ImgChannel channel = fs.create("XXX.TYP");
		TYPFile typFile = new TYPFile(channel, true);
		assertNotNull("typ file is created", typFile);
	}
}
