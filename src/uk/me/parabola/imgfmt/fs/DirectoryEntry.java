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
package uk.me.parabola.imgfmt.fs;

/**
 * @author Steve Ratcliffe
 */
public interface DirectoryEntry {
	/**
	 * Get the file name.
	 * @return The file name.
	 */
	String getName();

	/**
	 * Get the file extension.
	 * @return The file extension.
	 */
	String getExt();

	/**
	 * Get the file size.
	 * @return The size of the file in bytes.
	 */
	int getSize();
}
