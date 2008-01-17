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
 * Create date: Dec 23, 2007 Time: 1:43:07 PM
 */
package uk.me.parabola.imgfmt.app.labelenc;

import java.io.ByteArrayOutputStream;

/**
 * Decodes as as though every byte where a character without any translation.
 */
public class SimpleDecoder implements CharacterDecoder {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private boolean needReset;

	public boolean addByte(int b) {
		if (b == 0) {
			needReset = true;
			return true;
		}

		if (needReset) {
			needReset = false;
			out.reset();
		}

		out.write(b);
		return false;
	}

	public EncodedText getText() {
		byte[] ba = out.toByteArray();
		return new EncodedText(ba, ba.length);
	}
}
