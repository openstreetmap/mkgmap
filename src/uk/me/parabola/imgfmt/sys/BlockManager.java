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
 * Create date: 02-Dec-2006
 */
package uk.me.parabola.imgfmt.sys;

import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 * Used to allocate blocks to files.
 *
 * @author Steve Ratcliffe
 */
public class BlockManager {
	private int nextBlock;
	private int blockSize;

	private FileChannel file;

	public BlockManager(FileChannel file, int blockSize, int initialBlock) {
		this.file = file;
		this.blockSize = blockSize;
		this.nextBlock = initialBlock;
	}

	/**
	 * Well the algorithm is pretty simple - you just get the next unused block
	 * number.
	 *
	 * @return A block number that is free to be used.
	 */
	public int allocate() {
		return nextBlock++;
	}

	public int getCurrentBlock() {
		return nextBlock;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public int writeBlock(int bl, ByteBuffer buf) throws IOException {
		buf.flip();
		int rc = file.write(buf);
		return rc;
	}
}
