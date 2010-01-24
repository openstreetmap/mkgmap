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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;

import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The internal representation of a file in the file system.  In use it
 * should only be referred to by the {@link ImgChannel} interface.
 *
 * @author Steve Ratcliffe
 */
public class FileNode implements ImgChannel {
	private static final Logger log = Logger.getLogger(FileNode.class);

	private boolean open;
	private boolean writeable;
	private boolean readable;

	private final FileChannel file;
	private final BlockManager blockManager;
	private final Dirent dirent;

	// The position in this file
	private long position;

	private byte xorByte;

	/**
	 * Creates a new file in the file system.  You can treat this just like
	 * a regular file and write or read from it.
	 * Operations to two different files may not be interleaved although
	 * it may be possible to implement this.
	 *
	 * @param file The handle to the underlying file.
	 * @param dir The directory entry associated with this file.
	 * @param mode The mode "rw" for read and write etc.
	 */
	public FileNode(FileChannel file, Dirent dir, String mode)
	{
		this.file = file;
		this.dirent = dir;

		if (mode.indexOf('r') >= 0)
			readable = true;
		if (mode.indexOf('w') >= 0)
			writeable = true;
		if (!(readable || writeable))
			throw new IllegalArgumentException("File must be readable or writeable");

		blockManager = dir.getBlockManager();
		if (blockManager == null)
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
	 *
	 * @param dst The buffer into which bytes are to be transferred
	 *
	 * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the
	 * channel has reached end-of-stream
	 *
	 * @throws NonReadableChannelException If this channel was not opened for reading
	 * @throws ClosedChannelException If this channel is closed
	 * @throws AsynchronousCloseException If another thread closes this channel
	 * while the read operation is in progress
	 * @throws ClosedByInterruptException If another thread interrupts the
	 * current thread while the read operation is in progress, thereby closing
	 * the channel and setting the current thread's interrupt status
	 * @throws IOException If some other I/O error occurs
	 */
	public int read(ByteBuffer dst) throws IOException {
		if (!open)
			throw new ClosedChannelException();
		if (!readable)
			throw new NonReadableChannelException();

		int blockSize = blockManager.getBlockSize();

		long size = dst.remaining();
		long fileSize = dirent.getSize();
		if (position >= fileSize)
			return -1;
		size = Math.min(size, fileSize - position);

		int totalRead = 0;

		while (size > 0) {
			// Tet the logical block number, as we see it in our file.
			int lblock = (int) (position / blockSize);

			// Get the physical block number, the actual block number in
			// the underlying file.
			int pblock = dirent.getPhysicalBlock(lblock);
			if (pblock == 0xffff) {
				// We are at the end of the file.
				log.debug("at eof");
				break;
			}

			// Position the underlying file
			int off = (int) (position - lblock*blockSize);
			file.position((long) pblock * blockSize + off);

			int n = (int) size;
			if (n > blockSize)
				n = blockSize;

			if (off != 0)
				n = Math.min(n, blockSize - off);

			dst.limit(dst.position() + n);

			int pos = dst.position();
			int nr = file.read(dst);
			if (nr == -1)
				return -1;
			if (nr == 0)
				throw new IOException("Read nothing");

			if(xorByte != 0) {
				byte[] bufBytes = dst.array();
				for(int i = pos + n - 1; i >= pos; --i)
					bufBytes[i] ^= xorByte;
			}

			// Update the file positions
			size -= nr;
			position += nr;
			totalRead += nr;
		}

		log.debug("read ret", totalRead);
		return totalRead;
	}

	/**
	 * Writes a sequence of bytes to this channel from the given buffer.
	 * <p/>
	 * <p> An attempt is made to write up to <i>r</i> bytes to the channel,
	 * where <i>r</i> is the number of bytes remaining in the buffer, that is,
	 * <tt>dst.remaining()</tt>, at the moment this method is invoked.
	 * <p>The logical block has to be converted to a physical block in the
	 * underlying file.
	 *
	 * @param src The buffer from which bytes are to be retrieved
	 * @return The number of bytes written, possibly zero
	 * @throws NonWritableChannelException
	 *                             If this channel was not opened for writing
	 * @throws ClosedChannelException
	 *                             If this channel is closed
	 * @throws IOException If some other I/O error occurs
	 */
	public int write(ByteBuffer src) throws IOException {
		if (!open)
			throw new ClosedChannelException();

		int blockSize = blockManager.getBlockSize();

		// Get the size of this write
		int size = src.remaining();

		// Loop over each block, this is to support the case (which we may
		// not implement) of non-contiguous blocks.

		int totalWritten = 0;
		while (size > 0) {
			// Get the logical block, ie the block as we see it in our file.
			int lblock = (int) (position/blockSize);

			// First need to allocate enough blocks for this write. First check
			// if the block exists already
			int pblock = dirent.getPhysicalBlock(lblock);
			log.debug("lblock / pblock", lblock, '/', pblock);
			if (pblock == 0xffff) {
				log.debug("allocating new block");
				pblock = blockManager.allocate();
				dirent.addBlock(pblock);
			}

			// Position the underlying file, so that it is in the correct place.
			int off = (int) (position - lblock*blockSize);
			file.position((long) pblock * blockSize + off);

			int n = size;
			if (n > blockSize)
				n = blockSize;

			if (off != 0)
				n = Math.min(n, blockSize - off);

			src.limit(src.position() + n);

			// Write to the underlying file.
			int nw = file.write(src);
			if (nw == 0)
				throw new IOException("Wrote nothing");

			// Update the file positions
			size -= nw;
			position += nw;
			totalWritten += nw;

			// Update file size.
			if (position > dirent.getSize())
				dirent.setSize((int) position);
		}

		return totalWritten;
	}

	public long position() {
		return position;
	}

	public void position(long pos) {
		int blockSize = blockManager.getBlockSize();

		while (pos > position) {
			long lblock = position / blockSize;
			int pblock = dirent.getPhysicalBlock((int) lblock);

			if (pblock == 0xffff) {
				if (writeable) {
					log.debug("setting position allocating new block", lblock);
					pblock = blockManager.allocate();
					dirent.addBlock(pblock);
				}
			}
			position = (lblock+1) * blockSize;
		}

		this.position = pos;
	}

	/**
	 * Write out any unsaved data to disk.
	 *
	 * @throws IOException If there is an error writing to disk.
	 */
	private void sync() throws IOException {
		if (!writeable)
			return;
		
		// Ensure that a complete block is written out.
		int bs = blockManager.getBlockSize();
		long rem = bs - (file.position() % bs);

		ByteBuffer buf = ByteBuffer.allocate(blockManager.getBlockSize());

		// Complete any partial block.
		for (int i = 0; i < rem; i++)
			buf.put((byte) 0);

		buf.flip();
		file.write(buf);
	}

	public void setXorByte(byte xorByte) {
		this.xorByte = xorByte;
	}
}
