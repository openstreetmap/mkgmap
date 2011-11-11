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
package uk.me.parabola.mkgmap.typ;

import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.imgfmt.app.typ.BitmapImage;
import uk.me.parabola.imgfmt.app.typ.ColourInfo;
import uk.me.parabola.imgfmt.app.typ.Rgb;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypElement;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Much of the processing between lines and polygons is the same, these routines
 * are shared.
 *
 * @author Steve Ratcliffe
 */
public class CommonSection {
	private static final Set<String> seen = new HashSet<String>();
	protected final TypData data;

	protected CommonSection(TypData data) {
		this.data = data;
	}

	protected int decodeFontStyle(String value) {
		if (value.startsWith("NoLabel")) {
			return 1;
		} else if (value.startsWith("SmallFont")) {
			return 2;
		} else {
			warnUnknown("font value " + value);
			return 0;
		}
	}

	/**
	 * Read the colour lines from the XPM format image.
	 * @param numberColours Number of colours to read.
	 * @param charsPerPixel Number of characters for each pixel.
	 */
	protected ColourInfo readColourInfo(TokenScanner scanner, int numberColours, int charsPerPixel) {
		ColourInfo colourInfo = new ColourInfo(numberColours);
		for (int i = 0; i < numberColours; i++) {
			String line = scanner.readLine();

			// Strip the quotes
			line = line.substring(1, line.length() - 1);
			String colourTag = line.substring(0, charsPerPixel);
			if (line.length() < 8)
				throw new SyntaxException(scanner, "Short colour definition \"" + line);
			if (line.charAt(charsPerPixel + 3) == 'n'
					&& line.substring(charsPerPixel+3, charsPerPixel+7).equals("none"))
			{
				colourInfo.addTransparent(colourTag);

			} else {
				if (line.charAt(charsPerPixel + 3) != '#')
					throw new SyntaxException(scanner, "Expecting colour beginning with '#'");

				// The colour value as RRGGBB
				String colourValue = line.substring(5, 11);

				colourInfo.addColour(colourTag, new Rgb(colourValue));
			}
		}
		return colourInfo;
	}

	/**
	 * Read the bitmap part of a XPM image.
	 *
	 * In the TYP file, XPM is used when there is not really an image, so this is not
	 * always called.
	 */
	protected BitmapImage readImage(TokenScanner scanner, int w, int h, int cpp, ColourInfo colourInfo) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < h; i++) {
			String line = scanner.readLine();
			if (line.charAt(0) != '"' || line.charAt(line.length()-1) != '"')
				throw new SyntaxException(scanner, "xpm bitmap line not surrounded by quotes: " + line);
			sb.append(line.substring(1, line.length() - 1));
		}
		if (sb.length() != w*h*cpp)
			throw new SyntaxException(scanner, "Got " + sb.length() + " of image data, expected " + w * h * cpp);

		return new BitmapImage(w, h, cpp, colourInfo, sb.toString());
	}

	protected boolean commonKey(TokenScanner scanner, TypElement current, String name, String value) {
		if (name.equals("Type")) {
			int ival = Integer.decode(value);
			if (ival > 0x10000) {
				current.setType((ival >> 8) & 0x1ff);
				current.setSubType(ival & 0xff);
			} else {
				current.setType(ival & 0xff);
			}

		} else if (name.startsWith("String")) {
			current.addLabel(value);

		} else if (name.equals("Xpm")) {
			readXpm(scanner, current, value);

		} else if (name.equals("FontStyle")) {
			int font = decodeFontStyle(value);
			current.setFontStyle(font);

		} else if (name.equals("CustomColor")) {
			// I don't think we need to look at this, just use DayCustomColor etc.

		} else if (name.equals("DaycustomColor")) {
			current.setDayCustomColor(value);

		} else {
			return false;
		}
		
		return true;
	}

	protected void warnUnknown(String name) {
		if (seen.contains(name))
			return;

		seen.add(name);
		System.out.printf("Warning: tag '%s' not known\n", name);
	}

	/**
	 * Read an XMP image from the input scanner.
	 *
	 * Note that this is sometimes used just for colours so need to deal with
	 * different cases.
	 */
	private void readXpm(TokenScanner scanner, TypElement current, String heading) {
		String first = heading.substring(1, heading.length() - 1);
		String[] header = first.split(" +");

		int w = Integer.parseInt(header[0]);
		int h = Integer.parseInt(header[1]);
		int c = Integer.parseInt(header[2]);
		int cpp = Integer.parseInt(header[3]);

		// Files do not set this if there is no actual bitmap
		if (cpp == 0)
			cpp = 1;

		ColourInfo colourInfo = readColourInfo(scanner, c, cpp);
		current.setColourInfo(colourInfo);

		if (h > 0 && w > 0) {
			colourInfo.setHasBitmap(true);
			BitmapImage image = readImage(scanner, w, h, cpp, colourInfo);
			current.setImage(image);
		}
	}
}
