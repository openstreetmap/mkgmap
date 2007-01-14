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
 * Create date: 14-Jan-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

/**
 * Holds the bytes and length of an encoded character string used in a label.
 * The length of the byte array may be longer than the part that is actually
 * used, so the length property should always be used.
 *
 * Class is imutable.
 *
 * @author Steve Ratcliffe
 */
public class EncodedText {
	private final byte[] ctext;
	private final int length;

	public EncodedText(byte[] buf, int len) {
		this.ctext = buf;
		this.length = len;
	}

	public byte[] getCtext() {
		return ctext;
	}

	public int getLength() {
		return length;
	}
}
