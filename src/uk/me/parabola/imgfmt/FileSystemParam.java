/*
 * Copyright (C) 2006, 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
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
	private int directoryStartEntry = 2; // Always in terms of entries of 512 bytes
	private int reservedDirectoryBlocks = 202;
	private boolean gmapsupp;

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

	public int getDirectoryStartEntry() {
		return directoryStartEntry;
	}

	public void setDirectoryStartEntry(int directoryStartBlock) {
		this.directoryStartEntry = directoryStartBlock;
	}

	public int getReservedDirectoryBlocks() {
		return reservedDirectoryBlocks;
	}

	public void setReservedDirectoryBlocks(int blocks) {
		this.reservedDirectoryBlocks = blocks;
	}

	public boolean isGmapsupp() {
		return gmapsupp;
	}

	public void setGmapsupp(boolean gmapsupp) {
		this.gmapsupp = gmapsupp;
	}
}
