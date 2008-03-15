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

import uk.me.parabola.log.Logger;

import java.io.IOException;

/**
 * Base class for all the img files.  There is a common header that
 * all the sub-files share.  They also have means of reading and writing
 * themselves.
 * 
 * @author Steve Ratcliffe
 */
public abstract class ImgFile  {
	private static final Logger log = Logger.getLogger(ImgFile.class);

	private CommonHeader header;

	private WriteStrategy writer;
	private ReadStrategy reader;

	private boolean readable;
	private boolean writable;

	public void close() {
		try {
			sync();
			if (writer != null)
				writer.close();
		} catch (IOException e) {
			log.error("error on file close", e);
		}
	}

	public int position() {
		return writer.position();
	}

	protected void position(long pos) {
		writer.position(pos);
	}
	
	protected abstract void sync() throws IOException;

	protected WriteStrategy getWriter() {
		return writer;
	}

	protected void setWriter(WriteStrategy writer) {
		writable = true;
		this.writer = writer;
	}

	protected ReadStrategy getReader() {
		return reader;
	}

	protected void setReader(ReadStrategy reader) {
		readable = true;
		this.reader = reader;
	}

	protected CommonHeader getHeader() {
		return header;
	}

	protected final void setHeader(CommonHeader header) {
		this.header = header;
	}

	protected boolean isWritable() {
		return writable;
	}

	public boolean isReadable() {
		return readable;
	}
}
