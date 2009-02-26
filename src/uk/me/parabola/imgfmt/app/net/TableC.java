/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Author: Steve Ratcliffe, Robert Vollmert
 * Create date: 18-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * @author Steve Ratcliffe, Robert Vollmert
 */
public class TableC {
	// size of the table, excluding the size field
	private int size;

	private final List<RouteRestriction> restrictions = new ArrayList<RouteRestriction>();

	/**
	 * Write the table including size field.
	 */
	public void write(ImgFileWriter writer, int tablesOffset) {
		if (restrictions.isEmpty()) {
			writer.put((byte) 0);
			return;
		} else {
			byte b = getSizeBytes();
			assert size < (1 << 8*b);
			if (b == 1)
				writer.put((byte) size);
			else
				writer.putChar((char) size);
		}
		for (RouteRestriction restr : restrictions)
			restr.write(writer, tablesOffset);
	}

	/**
	 * Add a restriction.
	 *
	 * @param restr A new restriction.
	 * @return The offset into Table C at which the restriction will
	 *         be written.
	 */
	public int addRestriction(RouteRestriction restr) {
		int offset = size;
		restrictions.add(restr);
		size += restr.getSize();
		return offset;
	}

	/**
	 * The size in bytes of the size field. Also the size of
	 * restriction indices in the nodes area.
	 */
	public byte getSizeBytes() {
		if (size == 0)
			return 0;
		else if (size < 0x80)
			return 1; // allows 7 bit index (8th bit is flag)
		else if (size < 0x8000)
			return 2; // allows 15 bit index (16th bit is flag)
		else
			// XXX: haven't seen larger than 2, may well be possible
			throw new FormatException("too many restrictions");
	}

	public void propagateSizeBytes() {
		byte b = getSizeBytes();
		for (RouteRestriction restr : restrictions)
			restr.setOffsetSize(b);
	}
}
