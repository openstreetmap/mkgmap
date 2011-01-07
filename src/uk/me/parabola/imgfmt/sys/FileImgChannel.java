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

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * This is an implementation of ImgChannel that delegates to a regular channel.
 * It can therefore be used to read or write regular files on the file system.
 *
 * @author Steve Ratcliffe
 */
public class FileImgChannel implements ImgChannel {
	private final FileChannel channel;
	private long position;

	public FileImgChannel(String filename, String mode) {
		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(filename, mode);
		} catch (FileNotFoundException e) {
			throw new ReadFailedException("Could not open " + filename, e);
		}
		this.channel = raf.getChannel();
	}

	public FileImgChannel(FileChannel channel) {
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
	 * beginning of the file (the file within the .img file, not the beginning of the
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
		try {
			channel.position(pos);
			position = pos;
		} catch (IOException e) {
			throw new ReadFailedException("Could not seek", e);
		}
	}
}
