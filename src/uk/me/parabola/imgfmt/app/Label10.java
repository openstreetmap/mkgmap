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

/**
 * @author Steve Ratcliffe
 */
public class Label10 extends Label {
	public Label10(String text) {
		if (text != null)
			encodeText(text);
	}

	private void encodeText(String text) {

		byte[] buf = new byte[text.length() * 2];
		int off = 0;

		for (char c : text.toCharArray()) {
			put10(buf, off++, c);
		}

	}

	private void put10(byte[] buf, int off, char c) {
		int bitOff = 10 * off;
		int byteOff = bitOff/8;

		int shift = bitOff - 8*byteOff;

		
	}
}
