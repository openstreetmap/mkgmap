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
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The new multiple icon format.
 * There can be several icons at different resolutions.
 *
 * @author Steve Ratcliffe
 */
public class TypIconSet extends TypElement {
	private final List<Xpm> icons = new ArrayList<Xpm>();

	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		offset = writer.position();

		// Start with the number of icons
		writer.put((byte) icons.size());

		for (Xpm xpm : icons) {
			ColourInfo colourInfo = xpm.getColourInfo();
			int nbits = calcBits(colourInfo);
			writer.putChar((char) (nbits/2));
			writer.put((byte) 1);
			writer.put((byte) colourInfo.getWidth());
			writer.put((byte) colourInfo.getHeight());
			writeImage(writer, xpm);
		}
	}

	private int calcBits(ColourInfo colourInfo) {
		int bits = 0;
		int bpp = colourInfo.getBitsPerPixel();

		bits += colourInfo.getWidth() * colourInfo.getHeight() * bpp;
		bits += colourInfo.getNumberOfSColoursForCM() * 3 * 8;
		if (colourInfo.getNumberOfColours() == 0 && colourInfo.getColourMode() == 0x10)
			bits += 3*8;
		bits += 0x2c;
		return bits;
	}

	public void addIcon(Xpm xpm) {
		icons.add(xpm);
	}

	public String getLabel() {
		if (labels.isEmpty())
			return null;
		return labels.get(0).getText();
	}
}
