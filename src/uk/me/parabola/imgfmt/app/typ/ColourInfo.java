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

import uk.me.parabola.imgfmt.app.BitWriter;
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

	/**
	 * Add a colour for this element.
	 * @param tag The xpm tag that represents the colour.
	 * @param rgb The actual colour.
	 */
	public void addColour(String tag, Rgb rgb) {
		indexMap.put(tag, colours.size());
		colours.add(rgb);
		if (!rgb.isTransparent())
			numberOfSolidColours++;
	}

	/**
	 * Add a transparent colour. Convenience routine.
	 */
	public void addTransparent(String colourTag) {
		addColour(colourTag, new Rgb(0,0,0,0));
	}

	public void setHasBitmap(boolean hasBitmap) {
		this.hasBitmap = hasBitmap;
	}

	/**
	 * The colour scheme in use. This is a bitmask that has the following bits:
	 * 0 - Has night colour
	 * 1 - day background colour is transparent
	 * 2 - night background colour is transparent
	 * 3 - has bitmap
	 *
	 * If there is no night colour, then set the night background colour bit to be the same as
	 * the day one.
	 *
	 * @return The colour scheme bitmask. The term colour scheme is historical, it doesn't really
	 * describe it.
	 */
	public int getColourScheme() {
		if (numberOfColours == 0)
			numberOfColours = colours.size();
		
		int scheme = 0;
		if (hasBitmap)
			scheme |= S_HAS_BITMAP;

		if (numberOfColours == 4)
			scheme |= S_NIGHT;

		if (!hasBitmap && !hasBorder && numberOfColours == 2)
			scheme |= S_NIGHT | S_DAY_TRANSPARENT | S_NIGHT_TRANSPARENT;
		
		if (numberOfColours < 2 || colours.get(1).isTransparent())
			scheme |= S_DAY_TRANSPARENT;
		if (numberOfColours == 4 && colours.get(3).isTransparent())
			scheme |= S_NIGHT_TRANSPARENT;

		if ((scheme & S_NIGHT) == 0)
			if ((scheme & S_DAY_TRANSPARENT) != 0)
				scheme |= S_NIGHT_TRANSPARENT;

		return scheme;
	}

	/**
	 * Get the number of bits per pixel that will be used in the written bitmap.
	 *
	 * This depends on the colour mode and number of colours to be represented.
	 */
	public int getBitsPerPixel() {
		if (simple)
			return 1;

		int nc = numberOfSolidColours; // XXX may depend on colour mode

		int nbits = 8;
		if (colourMode == 0x10) {
			if (nc < 3)
				nbits = 2;
			else if (nc < 15) {
				nbits = 4;
			}
		} else {
			if (nc < 2)
				nbits = 1;
			else if (nc < 4)
				nbits = 2;
			else if (nc < 16)
				nbits = 4;
		}
		
		if (colourMode == 0) {
			if (nc == 0)
				nbits = 16;
		}

		return nbits;
	}

	/**
	 * Write out the colours only.
	 */
	public void write(ImgFileWriter writer) {
		if (colourMode == 0x20) {
			writeColours20(writer);
		} else {
			for (Rgb rgb : colours) {
				if (!rgb.isTransparent())
					rgb.write(writer, (byte) 0x10);
			}
		}
	}

	/**
	 * Write out the colours in the colormode=x20 case.
	 */
	private void writeColours20(ImgFileWriter writer) {
		BitWriter bw = new BitWriter();
		for (Rgb rgb : colours) {
			bw.putn(rgb.getB(), 8);
			bw.putn(rgb.getG(), 8);
			bw.putn(rgb.getR(), 8);

			int alpha = 0xff - rgb.getA();
			alpha = alphaRound4(alpha);

			bw.putn(alpha, 4);
		}
		writer.put(bw.getBytes(), 0, bw.getLength());
	}

	/**
	 * Round alpha value to four bits.
	 * @param alpha The original alpha value eg 0xf0.
	 * @return Rounded alpha to four bits eg 0xe.
	 */
	private int alphaRound4(int alpha) {
		int top = (alpha >> 4) & 0xf;
		int low = alpha & 0xf;

		int diff = low-top;
		if (diff > 8)
			top++;
		else if (diff < -8)
			top--;
		return top;
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

	public int getNumberOfSColoursForCM() {
		if (colourMode == 0x10)
			return numberOfSolidColours;
		else
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

	public void addAlpha(int alpha) {
		int last = colours.size();
		Rgb rgb = colours.get(last - 1);
		rgb = new Rgb(rgb, alpha);
		colours.set(last - 1, rgb);

		colourMode = 0x20;
	}
}
