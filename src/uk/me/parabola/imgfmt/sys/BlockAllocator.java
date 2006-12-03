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

/**
 * Used to allocate blocks to files.
 *
 * @author Steve Ratcliffe
 */
public class BlockAllocator {
	private int nextBlock;

	public BlockAllocator(int initialBlock) {
		this.nextBlock = initialBlock;
	}

	public int getNextBlock() {
		return nextBlock++;
	}

	public int getCurrentBlock() {
		return nextBlock;
	}
}
