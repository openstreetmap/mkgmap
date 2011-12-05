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
	private final List<RgbWithTag> colours = new ArrayList<RgbWithTag>();
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
		RgbWithTag cwt = new RgbWithTag(tag, rgb);
		colours.add(cwt);
	}

	/**
	 * Add a transparent colour. Convenience routine.
	 */
	public void addTransparent(String colourTag) {
		addColour(colourTag, new Rgb(0, 0, 0, 0));
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
		if (numberOfColours == 4 && (colours.get(3).isTransparent()))
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

		// number of colours includes the transparent pixel in colormode=0x10 so this
		// works for all colour modes.
		int nc = numberOfColours;
		if (nc == 0)
			return 24;
		else if (nc < 2)
			return 1;
		else if (nc < 4)
			return 2;
		else if (nc < 16)
			return 4;
		else
			return 8;
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

	public int getIndex(String tag) {
		Integer ind = indexMap.get(tag);

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

	public void setSimple(boolean simple) {
		this.simple = simple;
	}

	public void setHasBorder(boolean hasBorder) {
		this.hasBorder = hasBorder;
	}

	/**
	 * Replace the last pixel with a pixel with the same colour components and the given
	 * alpha.
	 *
	 * This is used when the alpha value is specified separately to the colour values in the
	 * input file.
	 * @param alpha The alpha value to be added to the pixel. This is a real alpha, not a transparency.
	 */
	public void addAlpha(int alpha) {
		int last = colours.size();
		RgbWithTag rgb = colours.get(last - 1);
		rgb = new RgbWithTag(rgb, alpha);
		colours.set(last - 1, rgb);
	}

	/**
	 * Analyse the colour pallet and normalise it.
	 *
	 * Try to work out what is required from the supplied colour pallet and set the colour mode
	 * and rearrange transparent pixels if necessary to be in the proper place.
	 *
	 * At the end we build the index from colour tag to pixel index.
	 *
	 * @param simple If this is a line or polygon.
	 * @return A string describing the validation failure.
	 */
	public String analyseColours(boolean simple) {
		setSimple(simple);
		
		if (simple) {
			// There can be up to four colours, no partial transparency, and a max of one transparent pixel
			// in each of the day/night sections.

			if (numberOfColours > 4)
				return ("Too many colours for a line or polygon");
			if (numberOfColours == 0)
				return "Line or polygon cannot have zero colours";

			// Putting the transparent pixel first is common, so reverse if found
			if (colours.get(0).isTransparent()) {
				if (numberOfColours < 2)
					return "Only colour cannot be transparent for line or polygon";
				swapColour(0, 1);
			}
			if (numberOfColours > 2 && colours.get(2).isTransparent()) {
				if (numberOfColours < 4)
					return "Only colour cannot be transparent for line or polygon";
				swapColour(2, 3);
			}

			// There can only be one transparent pixel per colour pair
			if (numberOfColours > 1 && colours.get(0).isTransparent())
				return "Both day foreground and background are transparent";
			if (numberOfColours > 3 && colours.get(2).isTransparent())
				return "Both night foreground and background are transparent";

		} else {
			int transIndex = 0; // index of last transparent pixel, only used when there is only one
			int nTrans = 0; // completely transparent
			int nAlpha = 0; // partially transparent
			int count = 0;  // total number of colours
			for (RgbWithTag rgb : colours) {
				if (rgb.isTransparent()) {
					nTrans++;
					transIndex = count;
				}

				if (rgb.getA() != 0xff && rgb.getA() != 0)
					nAlpha++;
				count++;
			}

			if (nAlpha > 0 || count == nTrans) {
				// If there is any partial transparency we need colour mode 0x20
				// Also if there is only one pixel and it is transparent, since otherwise there would be zero
				// solid colours and that is a special case used to indicate a true colour pixmap.
				colourMode = 0x20;

			} else if (nTrans == 1) {
				colourMode = 0x10;

				// Ensure the transparent pixel is at the end
				RgbWithTag rgb = colours.remove(transIndex);
				colours.add(rgb);
			} 
		}

		int count = 0;
		for (RgbWithTag rgb : colours) {
			indexMap.put(rgb.getTag(), count++);
			if (!rgb.isTransparent())
				numberOfSolidColours++;
		}

		return null;
	}

	private void swapColour(int c1, int c2) {
		RgbWithTag tmp = colours.get(c1);
		colours.set(c1, colours.get(c2));
		colours.set(c2, tmp);
	}
}
