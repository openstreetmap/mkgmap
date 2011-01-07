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
 * Create date: 27-Oct-2007
 */
package uk.me.parabola.imgfmt.sys;

/**
 * This is a special class for the header.  It makes it easier to bootstrap
 * the directory by having a special implementation that starts up by knowing
 * that the blocks in the header start from 0.
 *
 * @author Steve Ratcliffe
 */
class HeaderDirent extends Dirent {
	HeaderDirent(String name, BlockManager blockManager) {
		super(name, blockManager);
	}

	/**
	 * Converts from a logical block to a physical block.  This is a special
	 * version that returns the logical block number when the {@link Dirent} is not
	 * set up.  This allows us to bootstrap the reading of the header blocks.
	 * The header blocks always logical and physical blocks the same.
	 *
	 * @param lblock The logical block in the file.
	 * @return The corresponding physical block in the filesystem.
	 */
	public int getPhysicalBlock(int lblock) {
		if (isInitialized()) {
			log.debug("gpb (ok)");
			return super.getPhysicalBlock(lblock);
		} else {
			log.debug("gpb (not setup)");
			return lblock;
		}
	}

	/**
	 * Get the file size.  The file appears large until the first blocks are
	 * read in and then it will take on its actual size.
	 *
	 * @return The size of the file in bytes.
	 */
	public int getSize() {
		if (isInitialized())
			return super.getSize();
		else
			return getBlockManager().getBlockSize() * 32;
	}

	/**
	 * Always returns true as this is only used for the special header
	 * directory entry.
	 *
	 * @return Always returns true.
	 */
	public boolean isSpecial() {
		return true;
	}
}