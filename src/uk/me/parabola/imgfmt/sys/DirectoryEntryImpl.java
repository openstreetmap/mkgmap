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
 * Create date: 30-Nov-2006
 */
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.fs.DirectoryEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.apache.log4j.Logger;

/**
 * An entry within a directory.
 *
 * @author Steve Ratcliffe
 */
class DirectoryEntryImpl implements DirectoryEntry {
	static private Logger log = Logger.getLogger(DirectoryEntryImpl.class);
	
	// Constants.
	private static final int MAX_FILE_LEN = 8;
	private static final int MAX_EXT_LEN = 3;

	private static final int BLOCKS_TABLE_START = 0x20;

	// Filenames are a base+extension
	private String name;
	private String ext;

	// The file size.
	private int size;

	private int blockSize;

	// The block table holds all the blocks that belong to this file.  The
	// documentation says that
	private int nBlockTables;
	private int nblocks;
	private char[] blockTable;

	// The block number where this entries data starts.
	private int dataStart;

	private boolean special;

	public DirectoryEntryImpl(String name, int blockSize, int dataStart) {
		int dot;
		dot = name.indexOf('.');
		if (dot >= 0) {
			setName(name.substring(0, dot));
			setExt(name.substring(dot+1));
		} else
			throw new IllegalArgumentException("Filename did not have dot");

		this.blockSize = blockSize;
		this.dataStart = dataStart;

		nBlockTables = 1;
		blockTable = new char[(blockSize - BLOCKS_TABLE_START)/2];
		Arrays.fill(blockTable, (char) 0xffff);

	}

	void sync(FileChannel file) throws IOException {
		if (nBlockTables > 1) {
			throw new IllegalArgumentException("cannot deal with more than one block table yet");
		}
		ByteBuffer buf = ByteBuffer.allocate(blockSize);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		buf.put((byte) 1);

		log.debug("nm " + buf.position());

		buf.put(toBytes(name, MAX_FILE_LEN, (byte) ' '));
		buf.put(toBytes(ext, MAX_EXT_LEN, (byte) ' '));

		buf.putInt(size);

		// For an unknown reason, the 'sub-file part' must be three when it
		// the header block entry.
		if (special)
			buf.putChar((char) 0x03);
		else
			buf.putChar((char) 0);

		// Write out the allocation of blocks for this entry.
		buf.position(0x20);
		for (int i = 0; i < (blockSize - BLOCKS_TABLE_START) / 2; i++) {
			buf.putChar(blockTable[i]);
		}

		buf.flip();
		file.write(buf);
		log.debug("data starts at " + this.dataStart);
	}

	/**
	 * Get the file name.
	 * @return The file name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the file name.  It cannot be too long.
	 * @param name The file name.
	 */
	public void setName(String name) {
		if (name.length() != MAX_FILE_LEN)
			throw new IllegalArgumentException("File name is wrong size");
		this.name = name;
	}

	/**
	 * Get the file extension.
	 * @return The file extension.
	 */
	public String getExt() {
		return ext;
	}

	/**
	 * Set the file extension.  Can't be longer than three characters.
	 * @param ext The file extension.
	 */
	public void setExt(String ext) {
		log.debug("ext len" + ext.length());
		if (ext.length() != MAX_EXT_LEN)
			throw new IllegalArgumentException("File extension is wrong size");
		this.ext = ext;
	}

	/**
	 * Get the file size.
	 * @return The size of the file in bytes.
	 */
	public int getSize() {
		return size;
	}

	private byte[] toBytes(String s, int len, byte pad) {
		byte[] out = new byte[len];
		for (int i = 0; i < len; i++) {
			if (i > s.length()) {
				out[i] = pad;
			} else {
				out[i] = (byte) s.charAt(i);
			}
		}
		return out;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public void addBlock(int n) {
		blockTable[nblocks++] = (char) n;
		size += blockSize;
	}

	public void setSpecial(boolean special) {
		this.special = special;
	}
}
