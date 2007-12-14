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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import java.io.IOException;

/**
 *
 * @author Steve Ratcliffe
 */
public class TYPFile extends ImgFile {
	private static final Logger log = Logger.getLogger(TYPFile.class);


	public TYPFile(ImgChannel chan) {

		WriteStrategy writer = new BufferedWriteStrategy(chan);
		setWriter(writer);

		//position(HEADER_LEN + INFO_LEN);
	}

	public void sync() throws IOException {
		log.debug("syncing lbl file");

		//dataPos = position();

		// Reposition to re-write the header with all updated values.
		position(0);
		getHeader().writeHeader(getWriter());

		getWriter().put(Utils.toBytes("Some text for the label gap"));

		// Sync our writer.
		getWriter().sync();
	}
}