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
 * Create date: 06-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.io.IOException;

import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The NOD file that contains routing information.
 * 
 * @author Steve Ratcliffe
 */
public class NODFile extends ImgFile {
	private NODHeader nodHeader = new NODHeader();

	public NODFile(ImgChannel chan, boolean write) {
		setHeader(nodHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(NODHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			nodHeader.readHeader(getReader());
		}
	}

	protected void sync() throws IOException {
		if (!isWritable())
			return;

		// Do anything that is in structures and that needs to be dealt with.
		writeBody();

		// Now refresh the header
		position(0);
		getHeader().writeHeader(getWriter());

		getWriter().sync();
	}

	private void writeBody() {
		writeNodes();
	}

	private void writeNodes() {
		
	}
}
