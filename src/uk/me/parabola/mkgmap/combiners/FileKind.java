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

package uk.me.parabola.mkgmap.combiners;

/**
 * The different kinds of file that can given to mkgmap
 */
enum FileKind {
	// The file is an img file ie. it contains several sub-files.
	IMG_KIND,

	// The file is a plain file and it doesn't need to be extracted from a .img
	FILE_KIND,

	// The file is an img containing an MDR file
	MDR_KIND,

	// The file is a gmapsupp and contains an MPS file
	GMAPSUPP_KIND,

	// The file is of an unknown or unsupported kind, and so it should be ignored.
	UNKNOWN_KIND
}
