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
public class Mdr1Record extends ConfigBase {
	private final int mapNumber;
	private int indexOffset;

	public Mdr1Record(int mapNumber, MdrConfig config) {
		setConfig(config);
		this.mapNumber = mapNumber;
	}

	/**
	 * Write out this record.  If for the device, this is just a list of
	 * maps.
	 * @param writer Where to write to.
	 * @return The of bytes written.  This depends on if it is in device
	 * format.
	 */
	public int write(ImgFileWriter writer) {
		writer.putInt(mapNumber);
		if (!isForDevice())
			writer.putInt(indexOffset);
		return isForDevice()? 8: 4;
	}

	public void setIndexOffset(int indexOffset) {
		this.indexOffset = indexOffset;
	}
}
