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
package uk.me.parabola.imgfmt;

/**
 * Small class to hold all kinds of filesystem parameters. If a field
 * is not set then it is not used.
 *
 * @author Steve Ratcliffe
 */
public class FileSystemParam {
	private String mapDescription = "Open Street Map";
	private int blockSize = 512;
	private int directoryStartBlock = 2;
	private int reservedDirectoryBlocks = 202;

	public String getMapDescription() {
		return mapDescription;
	}

	public void setMapDescription(String mapDescription) {
		this.mapDescription = mapDescription;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}

	public int getDirectoryStartBlock() {
		return directoryStartBlock;
	}

	public void setDirectoryStartBlock(int directoryStartBlock) {
		this.directoryStartBlock = directoryStartBlock;
	}

	public int getReservedDirectoryBlocks() {
		return reservedDirectoryBlocks;
	}

	public void setReservedDirectoryBlocks(int blocks) {
		this.reservedDirectoryBlocks = blocks;
	}
}
