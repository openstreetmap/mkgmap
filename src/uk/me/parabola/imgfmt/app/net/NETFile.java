/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.BufferedWriteStrategy;
import uk.me.parabola.imgfmt.app.BufferedReadStrategy;
import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.IOException;

/**
 * The NET file.  This consists of information about roads.  It is not clear
 * what this file brings on its own (without NOD) but may allow some better
 * searching, street addresses etc.
 *
 * @author Steve Ratcliffe
 */
public class NETFile extends ImgFile {
	private NETHeader netHeader = new NETHeader();

	public NETFile(ImgChannel chan, boolean write) {
		setHeader(netHeader);
		if (write) {
			setWriter(new BufferedWriteStrategy(chan));
			position(NETHeader.HEADER_LEN);
		} else {
			setReader(new BufferedReadStrategy(chan));
			netHeader.readHeader(getReader());
		}
	}

	protected void sync() throws IOException {
		if (!isWritable())
			return;

		// Write out the actual file body.
		writeBody();

		getHeader().writeHeader(getWriter());
		getWriter().sync();
	}

	private void writeBody() {

	}
}
