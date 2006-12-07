/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Steve Ratcliffe
 */
public class RGNFile extends ImgFile {
	private static int HEADER_LEN = 29;

	public RGNFile(ImgChannel chan) {
		super(chan);
		setLength(HEADER_LEN);
		setType("GARMIN RGN");
	}

	public void writeHeader() throws IOException {
		ByteBuffer buf = allocateBuffer();

		buf.putInt(HEADER_LEN);
		buf.putInt(0);
		
		write(buf);
	}


	protected void writeBody() throws IOException {
	}
}
