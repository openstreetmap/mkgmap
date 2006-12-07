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

	private WriteStrategy writer;

	public ImgFile(ImgChannel chan) {
		this.chan = chan;
		writer = new BufferedWriteStrategy(chan);
	}

	public void close() {
		try {
			sync();
		} catch (IOException e) {
			log.warn("error on file close");
		}
	}

	public int position() {
		return writer.position();
	}
	
	private void sync() throws IOException {
		log.debug("writing header for " + type);
		writeCommonHeader();
		writeHeader();
		writeBody();
		writer.sync();
	}

	public void writeCommonHeader() throws IOException {

		putChar((char) length);
		put(Utils.toBytes(type, 10, (byte) 0));
		put((byte) 1);
		put((byte) 0);
		ByteBuffer buf = allocateBuffer();
//		Utils.setCreationTime(buf, new Date());
//		buf.flip();
		putChar((char) 0);
		put((byte) 0);
		put((byte) 0);
		put((byte) 0);
		put((byte) 0);
		put((byte) 0);

//		int n = write(buf);
//		log.debug("wrote " + n + " bytes for header");
	}

	protected int write(ByteBuffer buf) throws IOException {
		buf.flip();
//		return chan.write(buf);
		throw new IOException("not here");
	}

	protected ByteBuffer allocateBuffer() {
		// XXX may go private
		return chan.allocateBuffer();
	}

	protected abstract void writeHeader() throws IOException;

	protected abstract void writeBody() throws IOException;

	public void setLength(int length) {
		this.length = length;
	}

	public void setType(String type) {
		this.type = type;
	}


	/**
	 * Write out a 3 byte value in the correct byte order etc.
	 *
	 * @param val The value to write.
	 */
	public void put3(int val) {
		log.debug("put3 " + val);
		writer.put((byte) (val & 0xff));
		writer.putChar((char) (val >> 8));
	}

	public void putInt(int val) {
		writer.putInt(val);
	}
	public void putChar(char val) {
		writer.putChar(val);
	}
	public void put(byte val) {
		writer.put(val);
	}

	protected void put(byte[] val) {
		writer.put(val);
	}
}
