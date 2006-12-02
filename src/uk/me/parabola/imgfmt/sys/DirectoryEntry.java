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

/**
 * An entry within a directory.
 *
 * @author Steve Ratcliffe
 */
public class DirectoryEntry {
	// Constants.
	private static final int MAX_FILE_LEN = 12;
	private static final int MAX_EXT_LEN = 3;

	// Filenames are a base+extension
	private String name;
	private String ext;

	// The file size.
	private int size;

	/**
	 * Get the file name.
	 * @return The file name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the file name.  It cannot be too long.
	 * @param name The file name.
	 */
	public void setName(String name) {
		if (name.length() > MAX_FILE_LEN)
			throw new IllegalArgumentException("File name too long");
		this.name = name;
	}

	/**
	 * Get the file extension.
	 * @return The file extension.
	 */
	public String getExt() {
		return ext;
	}

	/**
	 * Set the file extension.  Can't be longer than three characters.
	 * @param ext The file extension.
	 */
	public void setExt(String ext) {
		if (name.length() > MAX_EXT_LEN)
			throw new IllegalArgumentException("File extension too long");
		this.ext = ext;
	}

	/**
	 * Get the file size.
	 * @return The size of the file in bytes.
	 */
	public int getSize() {
		return size;
	}
}
