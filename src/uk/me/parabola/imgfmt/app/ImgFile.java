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
	
	private int headerLength;
	private String type;

	private WriteStrategy writer;

	public void close() {
		try {
			sync();
		} catch (IOException e) {
			log.error("error on file close", e);
		}
	}

	public int position() {
		return writer.position();
	}

	public void position(int pos) {
		writer.position(pos);
	}
	
	public abstract void sync() throws IOException;

	/**
	 * Writes out the header that is common to all the file types.  It should
	 * be called by the sync() methods of subclasses when they are ready.
	 *
	 * @throws IOException If there is an error writing to the file.
	 */
	protected void writeCommonHeader() throws IOException {
		putChar((char) headerLength);
		put(Utils.toBytes(type, 10, (byte) 0));
		put((byte) 1);
		put((byte) 0);
		byte[] date = Utils.makeCreationTime(new Date());
		put(date);
	}

	public void setHeaderLength(int headerLength) {
		this.headerLength = headerLength;
	}

	public void setType(String type) {
		this.type = type;
	}


	public WriteStrategy getWriter() {
		return writer;
	}

	public void setWriter(WriteStrategy writer) {
		this.writer = writer;
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

	/**
	 * Write out a 4 byte value.
	 *
	 * @param val The integer value to write.
	 */
	public void putInt(int val) {
		writer.putInt(val);
	}

	/**
	 * Write out a 2 byte value.
	 *
	 * @param val The value to write.
	 */
	public void putChar(char val) {
		writer.putChar(val);
	}

	/**
	 * Write out a single byte value.
	 *
	 * @param val The value to write.
	 */
	public void put(byte val) {
		writer.put(val);
	}

	/**
	 * Write out a number of bytes from an array.
	 *
	 * @param val The values to write.
	 */
	protected void put(byte[] val) {
		writer.put(val);
	}

	protected void setWriteStrategy(WriteStrategy writer) {
		this.writer = writer;
	}
}
