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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Writeable;

/**
 * Holds colour information for elements in the typ file.
 *
 * The Colour information can relate to a bitmap or solid shapes.
 *
 * @author Steve Ratcliffe
 */
public class ColourInfo implements Writeable {
	private static final int S_NIGHT = 1;
	private static final int S_DAY_TRANSPARENT = 0x2;
	private static final int S_NIGHT_TRANSPARENT = 0x4;
	private static final int S_HAS_BITMAP = 0x8;

	private int numberOfColours;
	private int numberOfSolidColours;

	private boolean hasBitmap;
	private boolean hasBorder;
	private final List<Rgb> colours = new ArrayList<Rgb>();
	private final Map<String, Integer> indexMap = new HashMap<String, Integer>();

	private char width;
	private char height;
	private char charsPerPixel;

	private boolean simple = true;
	private char colourMode;

	public void addColour(String tag, Rgb rgb) {
		indexMap.put(tag, colours.size());
		colours.add(rgb);
		if (!rgb.isTransparent())
			numberOfSolidColours++;
	}

	public void addTransparent(String colourTag) {
		addColour(colourTag, new Rgb(0,0,0,0));
	}

	public void setHasBitmap(boolean hasBitmap) {
		this.hasBitmap = hasBitmap;
	}

	public int getColourScheme() {
		assert numberOfColours == colours.size();
		
		int scheme = 0;
		if (hasBitmap) {
			scheme |= S_HAS_BITMAP;
		}
		if (hasBitmap || hasBorder) {
			if (numberOfColours == 4) {
				scheme |= S_NIGHT;
				if (colours.get(3).isTransparent())
					scheme |= S_NIGHT_TRANSPARENT;
			}
			if (colours.get(1).isTransparent())
				scheme |= S_DAY_TRANSPARENT;
		} else {
			if (numberOfColours == 2)
				scheme |= S_NIGHT;
			scheme |= S_DAY_TRANSPARENT | S_NIGHT_TRANSPARENT;
		}

		// 0xa or 0xc may not work
		if (scheme == 0xa)
			scheme = 0xe;
		if (scheme == 0xc)
			scheme = 0x80;

		return scheme;
	}

	public int getBitsPerPixel() {
		if (simple) {
			return 1;
		}

		int nc = numberOfSolidColours; // XXX may depend on colour mode

		int nbits = 8;
		if (colourMode == 0) {
			if (nc < 2)
				nbits = 1;
			else if (nc < 4)
				nbits = 2;
			else if (nc < 16)
				nbits = 4;
		} else if (colourMode == 0x10) {
			if (nc < 3)
				nbits = 2;
			else if (nc < 15) {
				nbits = 4;
			}
		} else if (colourMode == 0x20) {
			if (nc < 2)
				nbits = 1;
			else if (nc < 4)
				nbits = 2;
			else if (nc < 16)
				nbits = 4;
		}

		return nbits;
	}

	public void write(ImgFileWriter writer) {
		for (Rgb rgb : colours) {
			if (!rgb.isTransparent())
				rgb.write(writer, (byte) 0x10);
		}
	}

	public int getIndex(String idx) {
		Integer ind = indexMap.get(idx);

		// If this is a simple bitmap (for line or polygon), then the foreground colour is
		// first and so has index 0, but we want the foreground to have index 1, so reverse.
		if (simple)
			ind = ~ind;

		return ind;
	}

	public void setWidth(int width) {
		this.width = (char) width;
	}

	public void setHeight(int height) {
		this.height = (char) height;
	}

	public void setNumberOfColours(int numberOfColours) {
		this.numberOfColours = numberOfColours;
	}

	public void setCharsPerPixel(int charsPerPixel) {
		this.charsPerPixel = (char) (charsPerPixel == 0 ? 1 : charsPerPixel);
	}

	public int getNumberOfColours() {
		return numberOfColours;
	}

	public int getNumberOfSolidColours() {
		return numberOfSolidColours;
	}

	public int getCharsPerPixel() {
		return charsPerPixel;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}

	public int getColourMode() {
		return colourMode;
	}

	public void setColourMode(int colourMode) {
		this.colourMode = (char) colourMode;
	}

	public void setSimple(boolean simple) {
		this.simple = simple;
	}

	public void setHasBorder(boolean hasBorder) {
		this.hasBorder = hasBorder;
	}
}
