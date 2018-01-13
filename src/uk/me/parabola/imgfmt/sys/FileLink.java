/*
 * Copyright (C) 2018.
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
package uk.me.parabola.imgfmt.sys;

import uk.me.parabola.imgfmt.Sized;

/**
 * Allow an ImgWriter to link into the FileNode.
 *
 * When writing a file in ImgFS.close() we need the actual sizes of the files
 * before they are written to the real file in order to calculate the directory
 * size.
 */
public interface FileLink {

	/**
	 * A buffering ImgWriter can supply methods to the FileNode to obtain its
	 * size and access to its sync() routine.
	 */
	public void link(Sized sized, Syncable syncable);
}
