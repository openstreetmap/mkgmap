/*
 * Copyright (C) 2011.
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
package uk.me.parabola.imgfmt.app.typ;

import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Holds the data for a polygon style.
 *
 * @author Steve Ratcliffe
 */
public class TypPolygon extends TypElement {

	private static final int F_LABEL = 0x10;
	private static final int F_EXTENDED = 0x20;

	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		offset = writer.position();

		ColourInfo colourInfo = xpm.getColourInfo();
		int scheme = colourInfo.getColourScheme();
		if (!labels.isEmpty())
			scheme |= F_LABEL;
		if (fontStyle != 0 || dayFontColour != null)
			scheme |= F_EXTENDED;

		writer.put((byte) scheme);

		colourInfo.write(writer);
		if (xpm.hasImage())
			xpm.writeImage(writer);

		// The labels have a length byte to show the number of bytes following. There is
		// also a flag in the length. The strings have a language number proceeding them.
		// The strings themselves are null terminated.
		if ((scheme & F_LABEL) != 0) {
			ByteBuffer out = makeLabelBlock(encoder);
			int flag = 1; // XXX What is this?

			// write out the length byte
			byte len = (byte) ((out.position() << 1) + flag);
			writer.put(len);

			// Prepare and write buffer
			out.flip();
			writer.put(out);
		}

		// The extension section hold font style and colour information for the labels.
		if ((scheme & F_EXTENDED) != 0) {
			writeExtendedFontInfo(writer);
		}
	}
}
