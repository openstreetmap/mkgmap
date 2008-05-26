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
 * Create date: 10-Jan-2007
 */
package uk.me.parabola.imgfmt.app;

import uk.me.parabola.imgfmt.app.labelenc.EncodedText;
import uk.me.parabola.log.Logger;

/**
 * Labels are used for names of roads, points of interest etc.
 *
 * There are different storage formats.
 *
 * 1. A 6 bit compact uppercase ascii format, that has escape codes for some
 * special characters.
 *
 * 2. An 8 bit format.  This seems to be a fairly straightforward latin-1 like
 * encoding with no tricks to reduce the amount of space required.
 *
 * @author Steve Ratcliffe
 */
public class Label {
	private static final Logger log = Logger.getLogger(Label.class);

	// The compressed form of the label text.
	private final byte[] ctext;
	private final int length;

	// The offset in to the data section.
	private int offset;

	public Label(EncodedText etext) {
		ctext = etext.getCtext();
		length = etext.getLength();
	}

	/**
	 * The length of the label in bytes as it will appear in the file.
	 * This is after the text has been converted to the label format being used.
	 * It will usually not be the same length as the text it is representing.
	 *
	 * @return Byte length of the label.
	 */
	public int getLength() {
		return length;
	}

	/**
	 * The offset of this label in the LBL file.  The first byte of this file
	 * is zero and an offset of zero means that the label has a zero length/is
	 * empty.
	 *
	 * @return The offset within the LBL file of this string.
	 */
	public int getOffset() {
		if (ctext == null)
			return 0;
		else
			return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 * Write this label to the given img file.
	 *
	 * @param writer The LBL file to write to.
	 */
	public void write(WriteStrategy writer) {
		if (log.isDebugEnabled())
			log.debug("put label", this.length);
		if (ctext != null)
			writer.put(ctext, 0, this.length);
	}
}
