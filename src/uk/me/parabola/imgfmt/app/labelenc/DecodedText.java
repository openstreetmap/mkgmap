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
package uk.me.parabola.imgfmt.app.labelenc;

import java.nio.charset.Charset;

/**
 * Holds information about a label that has been read in from an img file.
 *
 * @author Steve Ratcliffe
 */
public class DecodedText {
	/** The actual text as a proper java string */
	private final String text;

	/**
	 * Used during reading, the offset of the next label is at next byte that
	 * is added to the decoder plus this value.  The value is usually negative
	 * or zero.
	 */
	private int offsetAdjustment;

	/**
	 * The raw bytes as read.  If you are not changing character set you might
	 * want these to write the label out again.
	 *
	 * XXX do we really need this??
	 */
	private final byte[] rawBytes;

	public DecodedText(byte[] ba, Charset charSet) {
		text = new String(ba, 0, ba.length, charSet);
		rawBytes = ba;
	}


	public String getText() {
		return text;
	}

	public int getOffsetAdjustment() {
		return offsetAdjustment;
	}

	public void setOffsetAdjustment(int offsetAdjustment) {
		this.offsetAdjustment = offsetAdjustment;
	}
}
