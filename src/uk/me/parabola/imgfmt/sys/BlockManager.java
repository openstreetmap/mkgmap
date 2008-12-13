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
 * Create date: 25-Oct-2007
 */
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;

/**
 * This is used to allocate blocks for files in the filesystem/archive.
 *
 * @author Steve Ratcliffe
 */
class BlockManager {
	private static final Logger log = Logger.getLogger(BlockManager.class);

	private int currentBlock;
	private final int blockSize;
	private int maxBlock = 0xfffe;

	BlockManager(int blockSize, int initialBlock) {
		this.blockSize = blockSize;
		this.currentBlock = initialBlock;
	}

	/**
	 * Well the algorithm is pretty simple - you just get the next unused block
	 * number.
	 *
	 * @return A block number that is free to be used.
	 */
	public int allocate() {
		int n = currentBlock++;
		if (maxBlock > 0 && n > maxBlock) {
			log.error("overflowed directory with max block " + maxBlock + ", current=" + n);
			throw new ExitException("Directory overflow.  This is a bug in mkgmap");
		}
		return n;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public int getMaxBlock() {
		return maxBlock;
	}

	public void setMaxBlock(int maxBlock) {
		this.maxBlock = maxBlock;
	}
}
