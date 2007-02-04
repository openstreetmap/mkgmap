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
 * Create date: 04-Feb-2007
 */
package uk.me.parabola.imgfmt.sys;

/**
 * System interface to a directory entry.
 *
 * @author Steve Ratcliffe
 */
public interface SysDirEntry {

	/**
	 * Get the number of block tables used by this directory entry.
	 *
	 * @return The Number of block tables.
	 */
	public int getNBlockTables();

}
