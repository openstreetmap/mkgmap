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
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * An entry within a directory.  This holds its name and a list
 * of blocks that go to make up this file.
 *
 * A directory entry may take more than block in the file system.
 *
 * All documentation seems to point to the block numbers having to be
 * contiguous, but seems strange so I shall experiment.
 *
 * @author Steve Ratcliffe
 */
class Dirent implements DirectoryEntry {
	private static final Logger log = Logger.getLogger(Dirent.class);

	// Constants.
	private static final int MAX_FILE_LEN = 8;
	private static final int MAX_EXT_LEN = 3;

	// Offset of the block table in the directory entry block.
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

	private boolean special;

	Dirent(String name, int blockSize) {
		int dot;
		dot = name.indexOf('.');
		if (dot >= 0) {
			setName(name.substring(0, dot));
			setExt(name.substring(dot+1));
		} else
			throw new IllegalArgumentException("Filename did not have dot");

		this.blockSize = blockSize;

		nBlockTables = 1;
		blockTable = new char[(blockSize - BLOCKS_TABLE_START)/2];
		Arrays.fill(blockTable, (char) 0xffff);
	}

	/**
	 * Write this entry out to disk.
	 * TODO: we currently do not cope with the case where this takes more
	 * than one block.
	 *
	 * @param file The file to write to.
	 * @throws IOException If writing fails for any reason.
	 */
	void sync(FileChannel file) throws IOException {
		if (nBlockTables > 1) {
			throw new IllegalArgumentException("cannot deal with more than one block table yet");
		}
		ByteBuffer buf = ByteBuffer.allocate(blockSize);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		buf.put((byte) 1);

		log.debug("nm " + buf.position());

		buf.put(Utils.toBytes(name, MAX_FILE_LEN, (byte) ' '));
		buf.put(Utils.toBytes(ext, MAX_EXT_LEN, (byte) ' '));

		log.debug("dirent " + name + '.' + ext + " size is going to " + size);
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
	}

	/**
	 * Get the file name.
	 *
	 * @return The file name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the file name.  It cannot be too long.
	 *
	 * @param name The file name.
	 */
	private void setName(String name) {
		if (name.length() != MAX_FILE_LEN)
			throw new IllegalArgumentException("File name is wrong size "
			+ "was " + name.length() + ", should be " + MAX_FILE_LEN);
		this.name = name;
	}

	/**
	 * Get the file extension.
	 *
	 * @return The file extension.
	 */
	public String getExt() {
		return ext;
	}

	/**
	 * Set the file extension.  Can't be longer than three characters.
	 * @param ext The file extension.
	 */
	private void setExt(String ext) {
		log.debug("ext len" + ext.length());
		if (ext.length() != MAX_EXT_LEN)
			throw new IllegalArgumentException("File extension is wrong size");
		this.ext = ext;
	}

	/**
	 * Get the file size.
	 *
	 * @return The size of the file in bytes.
	 */
	public int getSize() {
		return size;
	}


	void setSize(int size) {
		log.debug("setting size " + getName() + getExt() + " to " + size);
		this.size = size;
	}

	/**
	 * Add a complete block and count the full size of it towards the
	 * file size.
	 *
	 * @param n The block number.
	 */
	void addFullBlock(int n) {
		// We do not currently deal with more than one directory inode block.
		if (nblocks >= 240)
			throw new FormatException("reached limit of file size");

		blockTable[nblocks++] = (char) n;
		size += blockSize;
	}

	/**
	 * Set for the first directory entry that covers the header and directory
	 * itself.
	 *
	 * @param special Set to true to mark as the special first entry.
	 */
	public void setSpecial(boolean special) {
		this.special = special;
	}

	/**
	 * Add a block without increasing the size of the file.
	 *
	 * @param n The block number.
	 */
	void addBlock(int n) {
		log.debug("adding block " + n + ", at " + nblocks);
		blockTable[nblocks++] = (char) n;
	}

	/**
	 * Converts from a logical block to a physical block.  If the block does
	 * not exist then 0xffff will be returned.
	 *
	 * @param lblock The logical block in the file.
	 * @return The corresponding physical block in the filesystem.
	 */
	public int getPhysicalBlock(int lblock) {
		if (lblock > blockTable.length)
			throw new IllegalArgumentException("can't deal with long files yet");

		int pblock = blockTable[lblock];
		return pblock;
	}
}
