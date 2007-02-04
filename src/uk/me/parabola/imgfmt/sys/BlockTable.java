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
 * Create date: 03-Feb-2007
 */
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.log.Logger;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Holds block numbers for a file.  It is part of the directory.  For a file
 * that needs more than one block several directory entries exist.  Each of
 * these has the header with the file name etc. in it, but the first one has
 * extra flags and info.
 *
 * <p>What is important here is that only part of a full block is used to
 * hold block numbers.
 *
 * @author Steve Ratcliffe
 */
class BlockTable {
	private static final Logger log = Logger.getLogger(BlockTable.class);

	// Offset of the block table in the directory entry block.
	private static final int BLOCKS_TABLE_START = 0x20;

	private final int tableSize;

	private int curroff;
	private final List<char[]> blocks;
	private char[] currTable;

	BlockTable(int blockSize) {
		this.tableSize = (blockSize - BLOCKS_TABLE_START)/2;

		blocks = new ArrayList<char[]>(20);
		newTable();
	}

	public void writeTable(ByteBuffer buf, int n) {
		char[] cbuf = blocks.get(n);
		log.debug("block with length", cbuf.length);
		for (char c : cbuf) {
			buf.putChar(c);
		}
	}

	/**
	 * Add the given block number to this directory.
	 *
	 * @param n The block number to add.
	 */
	public void addBlock(int n) {
		char[] thisTable = currTable;
		if (curroff >= tableSize)
			thisTable = newTable();

		thisTable[curroff++] = (char) n;
	}

	/**
	 * Given a logical block number, return the physical block number.
	 *
	 * @param lblock The logical block number, ie with respect to the file.
	 * @return The physical block number in the file system.
	 */
	public int physFromLogical(int lblock) {
		int blockNum = lblock / tableSize;
		int offset = lblock - blockNum * tableSize;
		if (blockNum >= blocks.size())
			return 0xffff;
		char[] cbuf = blocks.get(blockNum);
		return cbuf[offset];
	}

	/**
	 * Get the number of block tables.  This is the number of blocks that
	 * will be used in the on disk directory structure.
	 *
	 * @return The number of blocks tables.
	 */
	public int getNBlockTables() {
		return blocks.size();
	}

	/**
	 * Allocate a new block to hold more directory block numbers.
	 *
	 * @return Array for more numbers.
	 */
	private char[] newTable() {
		char[] b = new char[tableSize];
		Arrays.fill(b, (char) 0xffff);

		curroff = 0;
		blocks.add(b);
		currTable = b;

		return b;
	}
}
