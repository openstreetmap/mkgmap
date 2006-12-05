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
import uk.me.parabola.imgfmt.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * Base class for all the img files.  There is a common header that
 * all the sub-files share.  This will be handled in this class.
 * Also provides common services for writing the file.
 * 
 * @author Steve Ratcliffe
 */
public abstract class ImgFile {
	static private Logger log = Logger.getLogger(ImgFile.class);
	
	private int length;
	private String type;
	private ImgChannel chan;
	private int blockSize;

	public ImgFile(ImgChannel chan) {
		this.chan = chan;
	}

	public void close() {
		try {
			sync();
		} catch (IOException e) {
			log.warn("error on file close");
		}
	}

	private void sync() throws IOException {
		log.debug("writing header for " + type);
		writeCommonHeader();
		writeHeader();
	}

	public void writeCommonHeader() throws IOException {
		ByteBuffer buf = allocateBuffer();

		buf.putChar((char) length);
		buf.put(Utils.toBytes(type, 10, (byte) 0));
		buf.put((byte) 1);
		buf.put((byte) 0);
		Utils.setCreationTime(buf, new Date());

		int n = write(buf);
		log.debug("wrote " + n + " bytes for header");
	}

	protected int write(ByteBuffer buf) throws IOException {
		buf.flip();
		return chan.write(buf);
	}

	protected ByteBuffer allocateBuffer() {
		return chan.allocateBuffer();
	}

	protected abstract void writeHeader() throws IOException;

	public void setLength(int length) {
		this.length = length;
	}

	public void setType(String type) {
		this.type = type;
	}

	protected void put3(ByteBuffer buf, int val) {
		buf.put((byte) (val & 0xff));
		buf.putChar((char) (val >> 8));
	}
}
