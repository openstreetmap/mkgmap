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

import java.io.Closeable;
import java.io.IOException;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.log.Logger;

/**
 * Base class for all the img files.  There is a common header that
 * all the sub-files share.  They also have means of reading and writing
 * themselves.
 * 
 * @author Steve Ratcliffe
 */
public abstract class ImgFile implements Closeable {
	private static final Logger log = Logger.getLogger(ImgFile.class);

	private CommonHeader header;

	private ImgFileWriter writer;
	private ImgFileReader reader;

	private boolean readable;
	private boolean writable;

	public void close() {
		try {
			sync();
		} catch (IOException e) {
			log.debug("could not sync file");
		}
		Utils.closeFile(writer);
		Utils.closeFile(reader);
	}

	public int position() {
		return writer.position();
	}

	public CommonHeader getHeader() {
		return header;
	}

	public long getSize() {
		if (writable)
			return writer.getSize();
		throw new UnsupportedOperationException("getSize not implemented for read");
	}

	protected void position(long pos) {
		writer.position(pos);
	}

	protected final void sync() throws IOException {
		if (!writable)
			return;
		getWriter().sync();
	}

	protected ImgFileWriter getWriter() {
		return writer;
	}

	protected void setWriter(ImgFileWriter writer) {
		writable = true;
		this.writer = writer;
	}

	protected ImgFileReader getReader() {
		return reader;
	}

	protected void setReader(ImgFileReader reader) {
		readable = true;
		this.reader = reader;
	}

	protected final void setHeader(CommonHeader header) {
		this.header = header;
	}

	protected boolean isWritable() {
		return writable;
	}

	protected boolean isReadable() {
		return readable;
	}
}
