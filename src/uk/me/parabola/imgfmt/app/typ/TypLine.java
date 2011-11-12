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
 * A line as read from a typ.txt file.
 *
 * @author Steve Ratcliffe
 */
public class TypLine extends TypElement {
	private static final int F_LABEL = 0x1;
	private static final int F_USE_ROTATION = 0x2;
	private static final int F_EXTENDED = 0x4;

	private boolean useOrientation;
	private byte lineWidth;

	/**
	 * This is slightly different to the polygon case, but not much.
	 *
	 * The line width is held in the first byte along with the type.
	 * The colour scheme does not have a bit to say if a bitmap is used,
	 * as you can always have one.
	 *
	 * There is a border width that can be specified.
	 *
	 * @param encoder For the labels.
	 */
	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		offset = writer.position();

		byte flags = 0;

		if (!labels.isEmpty())
			flags |= F_LABEL;
		if (fontStyle != 0 || dayFontColour != null)
			flags |= F_EXTENDED;
		if (!useOrientation)
			flags |= F_USE_ROTATION;

		int height = 0;
		if (xpm.hasImage())
			height = xpm.getImage().getHeight();

		ColourInfo colourInfo = xpm.getColourInfo();
		int scheme = colourInfo.getColourScheme() & 0x7;

		writer.put((byte) ((scheme & 0x7) | (height << 3)));
		writer.put(flags);

		colourInfo.write(writer);
		if (xpm.hasImage())
			xpm.writeImage(writer);

		if (height == 0 /* && scheme != 6*/) {
			writer.put(lineWidth);
		}

		// The labels have a length byte to show the number of bytes following. There is
		// also a flag in the length. The strings have a language number proceeding them.
		// The strings themselves are null terminated.
		if ((flags & F_LABEL) != 0) {
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
		if ((flags & F_EXTENDED) != 0)
			writeExtendedFontInfo(writer);

	}

	public void setUseOrientation(boolean useOrientation) {
		this.useOrientation = useOrientation;
	}

	public void setLineWidth(int val) {
		lineWidth = (byte) val;
	}
}
