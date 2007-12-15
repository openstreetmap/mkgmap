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
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.ReadFailedException;

import java.nio.channels.ByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;

/**
 * This is an implementation of ImgChannel that delegates to a regular channel.
 * It can therefore be used to read or write regular files on the file system.
 *
 * @author Steve Ratcliffe
 */
public class FileImgChannel implements ImgChannel {
	private ByteChannel channel;
	private long position;

	public FileImgChannel(String filename) {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(filename, "r");
		} catch (FileNotFoundException e) {
			throw new ReadFailedException("Couldnot open " + filename, e);
		}
		this.channel = raf.getChannel();
	}

	public FileImgChannel(ByteChannel channel) {
		this.channel = channel;
	}

	public int read(ByteBuffer dst) throws IOException {
		int n = channel.read(dst);
		if (n > 0)
			position += n;
		return n;
	}

	public boolean isOpen() {
		return channel.isOpen();
	}

	public void close() throws IOException {
		channel.close();
	}

	public int write(ByteBuffer src) throws IOException {
		int n = channel.write(src);
		position += n;
		return n;
	}

	/**
	 * Get the file position. Note that this is a logical position relative to the
	 * begining of the file (the file within the .img file, not the begining of the
	 * .img file itself).
	 *
	 * @return The offset in bytes from the beginning of the file.
	 */
	public long position() {
		return position;
	}

	/**
	 * Set the position within the file.
	 *
	 * @param pos The position to set.
	 */
	public void position(long pos) {
		position = pos;
	}
}
