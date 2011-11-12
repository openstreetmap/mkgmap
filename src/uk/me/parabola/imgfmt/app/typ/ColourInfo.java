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

	private boolean hasBitmap;
	private final List<Rgb> colours = new ArrayList<Rgb>();
	private final Map<String, Integer> indexMap = new HashMap<String, Integer>();

	private int width;
	private int height;
	private int charsPerPixel;


	public void addColour(String tag, Rgb rgb) {
		indexMap.put(tag, colours.size());
		colours.add(rgb);
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

		return scheme;
	}

	public void write(ImgFileWriter writer) {
		for (Rgb rgb : colours) {
			if (!rgb.isTransparent())
				rgb.write(writer, (byte) 0x10);
		}
	}

	public int getIndex(String idx) {
		return indexMap.get(idx);
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void setNumberOfColours(int numberOfColours) {
		this.numberOfColours = numberOfColours;
	}

	public void setCharsPerPixel(int charsPerPixel) {
		this.charsPerPixel = charsPerPixel == 0 ? 1 : charsPerPixel;
	}

	public int getNumberOfColours() {
		return numberOfColours;
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
}
