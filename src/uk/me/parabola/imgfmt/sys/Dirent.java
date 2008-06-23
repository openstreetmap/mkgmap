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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An entry within a directory.  This holds its name and a list
 * of blocks that go to make up this file.
 *
 * A directory entry may take more than block in the file system.
 *
 * <p>All documentation seems to point to the block numbers having to be
 * contiguous, but seems strange so I shall experiment.
 *
 * <p>Entries are in blocks of 512 bytes, regardless of the block size.
 *
 * @author Steve Ratcliffe
 */
class Dirent implements DirectoryEntry {
	protected static final Logger log = Logger.getLogger(Dirent.class);

	// Constants.
	static final int MAX_FILE_LEN = 8;
	static final int MAX_EXT_LEN = 3;

	// Offsets
	static final int OFF_FILE_USED = 0x00;
	static final int OFF_NAME = 0x01;
	static final int OFF_EXT = 0x09;
	static final int OFF_FLAG = 0x10;
	static final int OFF_FILE_PART = 0x11;
	private static final int OFF_SIZE = 0x0c;

	static final int ENTRY_SIZE = 512;

	// Filenames are a base+extension
	private String name;
	private String ext;

	// The file size.
	private int size;

	private final BlockManager blockManager;

	// The block table holds all the blocks that belong to this file.  The
	// documentation suggests that block numbers are always contiguous.
	private BlockTable blockTable;

	private boolean special;
	private static final int OFF_USED_FLAG = 0;
	private boolean initialized;

	Dirent(String name, BlockManager blockManager) {
		this.blockManager = blockManager;

		int dot;
		dot = name.indexOf('.');
		if (dot >= 0) {
			setName(name.substring(0, dot));
			setExt(name.substring(dot+1));
		} else
			throw new IllegalArgumentException("Filename did not have dot");

		blockTable = new BlockTable();
	}

	/**
	 * Write this entry out to disk.  Note that these are 512 bytes, regardless
	 * of the blocksize.
	 *
	 * @param file The file to write to.
	 * @throws IOException If writing fails for any reason.
	 */
	void sync(ImgChannel file) throws IOException {
		int ntables = blockTable.getNBlockTables();
		ByteBuffer buf = ByteBuffer.allocate(ENTRY_SIZE * ntables);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		for (int part = 0; part < ntables; part++) {
			log.debug("position at part", part, "is", buf.position());
			
			buf.put((byte) 1);

			buf.put(Utils.toBytes(name, MAX_FILE_LEN, (byte) ' '));
			buf.put(Utils.toBytes(ext, MAX_EXT_LEN, (byte) ' '));

			// Size is only present in the first part
			if (part == 0) {
				log.debug("dirent", name, '.', ext, "size is going to", size);
				buf.putInt(size);
			} else {
				buf.putInt(0);
			}

			buf.put((byte) (special? 0x3: 0));
			buf.putChar((char) part);

			// Write out the allocation of blocks for this entry.
			buf.position(ENTRY_SIZE * part + 0x20);
			blockTable.writeTable(buf, part);
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
	 * Get the file extension.
	 *
	 * @return The file extension.
	 */
	public String getExt() {
		return ext;
	}

	/**
	 * The full name is of the form 8+3 with a dot in between the name and
	 * extension.  The full name is used as the index in the directory.
	 *
	 * @return The full name.
	 */
	public String getFullName() {
		return name + '.' + ext;
	}

	/**
	 * Read in the block numbers from the given buffer.  If this is the first
	 * directory block for this file, then the size is set too.
	 *
	 * @param buf The data as read from the file.
	 */
	void initBlocks(ByteBuffer buf) {

		byte used = buf.get(OFF_USED_FLAG);
		if (used != 1)
			return;

		int part = buf.get(OFF_FILE_PART) & 0xff;
		if (part == 0 || (isSpecial() && part == 3))
			size = buf.getInt(OFF_SIZE);

		blockTable.readTable(buf);
		initialized = true;
	}

	/**
	 * Get the file size.
	 *
	 * @return The size of the file in bytes.
	 */
	public int getSize() {
		return size;
	}

	/**
	 * Set the file name.  The name should be exactly eight characters long
	 * and it is truncated or left padded with zeros to make this true.
	 *
	 * @param name The file name.
	 */
	private void setName(String name) {
		int len = name.length();
		if (len > MAX_FILE_LEN) {
			this.name = name.substring(0, 8);
		} else if (len < MAX_FILE_LEN) {
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < MAX_FILE_LEN - len; i++) {
				sb.append('0');
			}
			sb.append(name);
			this.name = sb.toString();
		} else
			this.name = name;
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
	 * The number of blocks that the header covers.  The header includes
	 * the directory for the purposes of this routine.
	 *
	 * @return The total number of header basic blocks (blocks of 512 bytes).
	 */
	int numberHeaderBlocks() {
		return blockTable.getNBlockTables();
	}

	void setSize(int size) {
		if (log.isDebugEnabled())
			log.debug("setting size", getName(), getExt(), "to", size);
		this.size = size;
	}

	/**
	 * Add a block without increasing the size of the file.
	 *
	 * @param n The block number.
	 */
	void addBlock(int n) {
		blockTable.addBlock(n);
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

	public boolean isSpecial() {
		return special;
	}

	/**
	 * Converts from a logical block to a physical block.  If the block does
	 * not exist then 0xffff will be returned.
	 *
	 * @param lblock The logical block in the file.
	 * @return The corresponding physical block in the filesystem.
	 */
	public int getPhysicalBlock(int lblock) {
		return blockTable.physFromLogical(lblock);
	}

	public BlockManager getBlockManager() {
		return blockManager;
	}

	protected void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}
	
	protected boolean isInitialized() {
		return initialized;
	}
}
