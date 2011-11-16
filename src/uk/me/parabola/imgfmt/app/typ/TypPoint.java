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

import java.nio.charset.CharsetEncoder;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Represents a POI in the typ file.
 *
 * @author Steve Ratcliffe
 */
public class TypPoint extends TypElement {
	private Xpm nightXpm;
	private static final byte F_BITMAP = 0x1;
	private static final byte F_NIGHT_XPM = 0x2;
	private static final byte F_LABEL = 0x4;
	private static final byte F_EXTENDED_FONT = 0x8;

	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		offset = writer.position();

		byte flags = F_BITMAP;

		if (nightXpm != null)
			flags |= F_NIGHT_XPM;
		
		if (!labels.isEmpty())
			flags |= F_LABEL;

		if (fontStyle != 0 || dayFontColour != null || nightFontColour != null)
			flags |= F_EXTENDED_FONT;

		writer.put(flags);

		// Width and height is the same for day and night images, so it is written once only.
		ColourInfo colourInfo = xpm.getColourInfo();
		writer.put((byte) colourInfo.getWidth());
		writer.put((byte) colourInfo.getHeight());

		// Day or only image
		writeImage(writer, xpm);

		if ((flags & F_NIGHT_XPM) != 0)
			writeImage(writer, nightXpm);

		if ((flags & F_LABEL) != 0)
			writeLabelBlock(writer, encoder);

		if ((flags & F_EXTENDED_FONT) != 0)
			writeExtendedFontInfo(writer);
	}

	public void setNightXpm(Xpm nightXpm) {
		this.nightXpm = nightXpm;
	}
}
