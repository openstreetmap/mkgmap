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
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.fs.ImgChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

/**
 * @author Steve Ratcliffe
 */
public class FileNode implements ImgChannel {
	private boolean open;
	private boolean writeable;
	private boolean readable;

	private FileSystem fs;
	private Dirent dir;

	/**
	 * Creates a new file in the file system.  You can treat this just like
	 * a regular file and write or read from it.
	 * Operations to two different files may not be interleaved although
	 * it may be possible to implement this.
	 *
	 * @param fs The file system that file file is being created in.
	 * @param dir The directory entry associated with this file.
	 * @param mode The mode "rw" for read and write etc.
	 */
	public FileNode(FileSystem fs, Dirent dir, String mode) {
		this.dir = dir;
		this.fs = fs;

		if (mode.indexOf('r') >= 0)
			readable = true;
		if (mode.indexOf('w') >= 0)
			writeable = true;
		if (!(readable || writeable))
			throw new IllegalArgumentException("File must be readable or writeable");
		if (fs == null)
			throw new IllegalArgumentException("no file system supplied");
		
		open = true;
	}

	/**
	 * Closes this channel.
	 * <p/>
	 * <p> After a channel is closed, any further attempt to invoke I/O
	 * operations upon it will cause a {@link ClosedChannelException} to be
	 * thrown.
	 * <p/>
	 * <p> If this channel is already closed then invoking this method has no
	 * effect.
	 * <p/>
	 * <p> This method may be invoked at any time.  If some other thread has
	 * already invoked it, however, then another invocation will block until
	 * the first invocation is complete, after which it will return without
	 * effect. </p>
	 *
	 * @throws IOException If an I/O error occurs
	 */
	public void close() throws IOException {
		if (!open)
			return;

		sync();

		open = false;
		readable = false;
		writeable = false;
	}

	/**
	 * Tells whether or not this channel is open.  </p>
	 *
	 * @return <tt>true</tt> if, and only if, this channel is open
	 */
	public boolean isOpen() {
		return open;
	}

	/**
	 * Reads a sequence of bytes from this channel into the given buffer.
	 * <p/>
	 * <p> An attempt is made to read up to <i>r</i> bytes from the channel,
	 * where <i>r</i> is the number of bytes remaining in the buffer, that is,
	 * <tt>dst.remaining()</tt>, at the moment this method is invoked.
	 * <p/>
	 * <p> Suppose that a byte sequence of length <i>n</i> is read, where
	 * <tt>0</tt>&nbsp;<tt>&lt;=</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
	 * This byte sequence will be transferred into the buffer so that the first
	 * byte in the sequence is at index <i>p</i> and the last byte is at index
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>,
	 * where <i>p</i> is the buffer's position at the moment this method is
	 * invoked.  Upon return the buffer's position will be equal to
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
	 * <p/>
	 * <p> A read operation might not fill the buffer, and in fact it might not
	 * read any bytes at all.  Whether or not it does so depends upon the
	 * nature and state of the channel.  A socket channel in non-blocking mode,
	 * for example, cannot read any more bytes than are immediately available
	 * from the socket's input buffer; similarly, a file channel cannot read
	 * any more bytes than remain in the file.  It is guaranteed, however, that
	 * if a channel is in blocking mode and there is at least one byte
	 * remaining in the buffer then this method will block until at least one
	 * byte is read.
	 * <p/>
	 * <p> This method may be invoked at any time.  If another thread has
	 * already initiated a read operation upon this channel, however, then an
	 * invocation of this method will block until the first operation is
	 * complete. </p>
	 *
	 * @param dst The buffer into which bytes are to be transferred
	 * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the
	 *         channel has reached end-of-stream
	 * @throws NonReadableChannelException
	 *                             If this channel was not opened for reading
	 * @throws ClosedChannelException
	 *                             If this channel is closed
	 * @throws AsynchronousCloseException
	 *                             If another thread closes this channel
	 *                             while the read operation is in progress
	 * @throws ClosedByInterruptException
	 *                             If another thread interrupts the current thread
	 *                             while the read operation is in progress, thereby
	 *                             closing the channel and setting the current thread's
	 *                             interrupt status
	 * @throws IOException If some other I/O error occurs
	 */
	public int read(ByteBuffer dst) throws IOException {
		if (!open)
			throw new ClosedChannelException();
		if (!readable)
			throw new NonReadableChannelException();
		return 0;
	}

	/**
	 * Writes a sequence of bytes to this channel from the given buffer.
	 * <p/>
	 * <p> An attempt is made to write up to <i>r</i> bytes to the channel,
	 * where <i>r</i> is the number of bytes remaining in the buffer, that is,
	 * <tt>dst.remaining()</tt>, at the moment this method is invoked.
	 * <p/>
	 * <p> Suppose that a byte sequence of length <i>n</i> is written, where
	 * <tt>0</tt>&nbsp;<tt>&lt;=</tt>&nbsp;<i>n</i>&nbsp;<tt>&lt;=</tt>&nbsp;<i>r</i>.
	 * This byte sequence will be transferred from the buffer starting at index
	 * <i>p</i>, where <i>p</i> is the buffer's position at the moment this
	 * method is invoked; the index of the last byte written will be
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>&nbsp;<tt>-</tt>&nbsp;<tt>1</tt>.
	 * Upon return the buffer's position will be equal to
	 * <i>p</i>&nbsp;<tt>+</tt>&nbsp;<i>n</i>; its limit will not have changed.
	 * <p/>
	 * <p> Unless otherwise specified, a write operation will return only after
	 * writing all of the <i>r</i> requested bytes.  Some types of channels,
	 * depending upon their state, may write only some of the bytes or possibly
	 * none at all.  A socket channel in non-blocking mode, for example, cannot
	 * write any more bytes than are free in the socket's output buffer.
	 * <p/>
	 * <p> This method may be invoked at any time.  If another thread has
	 * already initiated a write operation upon this channel, however, then an
	 * invocation of this method will block until the first operation is
	 * complete. </p>
	 *
	 * @param src The buffer from which bytes are to be retrieved
	 * @return The number of bytes written, possibly zero
	 * @throws NonWritableChannelException
	 *                             If this channel was not opened for writing
	 * @throws ClosedChannelException
	 *                             If this channel is closed
	 * @throws AsynchronousCloseException
	 *                             If another thread closes this channel
	 *                             while the write operation is in progress
	 * @throws ClosedByInterruptException
	 *                             If another thread interrupts the current thread
	 *                             while the write operation is in progress, thereby
	 *                             closing the channel and setting the current thread's
	 *                             interrupt status
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(ByteBuffer src) throws IOException {
		if (!open)
			throw new ClosedChannelException();

		return 0;
	}

	/**
	 * Write out any unsaved data to disk.
	 *
	 * @throws IOException If there is an error writing to disk.
	 */
	private void sync() throws IOException {
		if (dir.getSize() == 0) {
			// If the size is zero then allocate a block anyway.  Not a very
			// realistic problem, done mainly for the testing phase.
			ByteBuffer buf = ByteBuffer.allocate(fs.getBlockSize());
			while (buf.hasRemaining())
				buf.put((byte) 0);
			int b = fs.allocateBlock();
			dir.addBlock(b);
			fs.writeBlock(b, buf);
		}
	}

}
