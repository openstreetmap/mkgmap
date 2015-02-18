/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

import java.io.File;

import uk.me.parabola.imgfmt.app.srt.Sort;

/**
 * Configuration for the MDR file.
 * Mostly used when creating a file as there are a number of different options
 * in the way that it is done.
 *
 * @author Steve Ratcliffe
 */
public class MdrConfig {
	//private static final int DEFAULT_HEADER_LEN = 286;
	private static final int DEFAULT_HEADER_LEN = 568;

	private boolean writable;
	private boolean forDevice;
	private int headerLen = DEFAULT_HEADER_LEN;
	private Sort sort;
	private File outputDir;
	private boolean splitName;

	/**
	 * True if we are creating the file, rather than reading it.
	 */
	public boolean isWritable() {
		return writable;
	}

	public void setWritable(boolean writable) {
		this.writable = writable;
	}

	/**
	 * The format that is used by the GPS devices is different to that used
	 * by Map Source. This parameter says which to do.
	 * @return True if we are creating the the more compact format required
	 * for a device.
	 */
	public boolean isForDevice() {
		return forDevice;
	}

	public void setForDevice(boolean forDevice) {
		this.forDevice = forDevice;
	}

	/**
	 * There are a number of different header lengths in existence.  This
	 * controls what sections can exist (and perhaps what must exist).
	 * @return The header length.
	 */
	public int getHeaderLen() {
		return headerLen;
	}

	public void setHeaderLen(int headerLen) {
		this.headerLen = headerLen;
	}

	public Sort getSort() {
		return sort;
	}

	public void setSort(Sort sort) {
		this.sort = sort;
	}

	public File getOutputDir() {
		return outputDir;
	}

	public void setOutputDir(String outputDir) {
		if (outputDir != null)
			this.outputDir = new File(outputDir);
	}

	public void setSplitName(boolean splitName) {
		this.splitName = splitName;
	}

	public boolean isSplitName() {
		return splitName;
	}
}
