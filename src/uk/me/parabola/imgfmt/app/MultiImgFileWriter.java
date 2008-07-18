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
 * Create date: 18-Jul-2008
 */
package uk.me.parabola.imgfmt.app;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 *
 * @author Steve Ratcliffe
 * @deprecated dont think this is useful
 */
public class MultiImgFileWriter extends BufferedImgFileWriter {
	private ByteBuffer[] bufs;

	public MultiImgFileWriter(ImgChannel chan, int nsections) {
		super(chan);
		bufs = new ByteBuffer[nsections];
		for (int i = 0; i < bufs.length; i++) {
			bufs[i] = ByteBuffer.allocate(INIT_SIZE);
			bufs[i].order(ByteOrder.LITTLE_ENDIAN);
		}
		setBuffer(bufs[0]);
	}

	public void switchBuffer(int n) {
		assert n < bufs.length;
		setBuffer(bufs[n]);
	}
}
