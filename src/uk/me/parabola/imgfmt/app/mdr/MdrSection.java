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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * @author Steve Ratcliffe
 */
public abstract class MdrSection extends ConfigBase {
	/**
	 * Write out the contents of this section.
	 * @param writer Where to write it.
	 */
	public abstract void writeSectData(ImgFileWriter writer);

	/**
	 * The size of a record in the section.  This is not a constant and
	 * might vary on various factors, such as the file version, if we are
	 * preparing for a device, the number of maps etc.
	 *
	 * @return The size of a record in this section.
	 */
	public abstract int getItemSize();
}
