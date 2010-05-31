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
package uk.me.parabola.imgfmt.app;

import java.io.Closeable;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.util.Configurable;

/**
 * Base class for all the img sub file reading classes.
 *
 * @author Steve Ratcliffe
 */
public abstract class ImgReader implements Closeable, Configurable {

	private CommonHeader header;
	private ImgFileReader reader;

	public void close() {
		Utils.closeFile(reader);
	}

	protected long position() {
		return reader.position();
	}

	protected void position(long pos) {
		reader.position(pos);
	}

	public CommonHeader getHeader() {
		return header;
	}

	protected final void setHeader(CommonHeader header) {
		this.header = header;
	}

	protected ImgFileReader getReader() {
		return reader;
	}

	protected void setReader(ImgFileReader reader) {
		this.reader = reader;
	}
}