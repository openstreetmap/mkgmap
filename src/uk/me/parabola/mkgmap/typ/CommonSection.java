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

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.imgfmt.app.typ.BitmapImage;
import uk.me.parabola.imgfmt.app.typ.ColourInfo;
import uk.me.parabola.imgfmt.app.typ.Rgb;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypElement;
import uk.me.parabola.imgfmt.app.typ.Xpm;
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
	 */
	protected ColourInfo readColourInfo(TokenScanner scanner, String header) {

		ColourInfo colourInfo = new ColourInfo();
		parseXpmHeader(scanner, colourInfo, header);

		for (int i = 0; i < colourInfo.getNumberOfColours(); i++) {
			// Tricky to do this with TokenScanner, since even spaces are significant sometimes.
			// TODO: change scanner maybe?
			String line = scanner.readLine();

			int cpp = colourInfo.getCharsPerPixel();
			String colourTag = line.substring(1, 1+cpp);
			System.out.printf("tag '%s'\n", colourTag);

			int ind = cpp+1;
			while (line.charAt(ind) == ' ' || line.charAt(ind) == '\t')
				ind++;

			if (line.charAt(ind++) != 'c')
				throw new SyntaxException(scanner, "Expecting 'c' in colour definition: " + line);

			while (line.charAt(ind) == ' ' || line.charAt(ind) == '\t')
				ind++;

			if (line.charAt(ind) == '#') {
				int start = ++ind;
				while (Character.isLetterOrDigit(line.charAt(ind))) {
					ind++;
				}
				int end = ind;

				String colour = line.substring(start, end);
				colourInfo.addColour(colourTag, new Rgb(colour));

			} else if (line.charAt(ind) == 'n') {
				if (line.substring(ind, ind + 4).equals("none"))
					colourInfo.addTransparent(colourTag);
				else
					throw new SyntaxException(scanner, "Unrecognised colour in: " + line);
			} else {
				throw new SyntaxException(scanner, "Cannot recognise colour definition: " + line);
			}
		}
		return colourInfo;
	}

	/**
	 * Parse the XPM header in a typ file.
	 *
	 * There are extensions compared to a regular XPM file.
	 *
	 * @param scanner Only for reporting syntax errors.
	 * @param info Information read from the string is stored here.
	 * @param header The string containing the xpm header and other extended data provided on the
	 * same line.
	 */
	private void parseXpmHeader(TokenScanner scanner, ColourInfo info, String header) {
		TokenScanner s2 = new TokenScanner("string", new StringReader(header));

		if (s2.checkToken("\""))
			s2.nextToken();

		try {
			info.setWidth(s2.nextInt());
			info.setHeight(s2.nextInt());
			info.setNumberOfColours(s2.nextInt());
			info.setCharsPerPixel(s2.nextInt());
		} catch (NumberFormatException e) {
			throw new SyntaxException(scanner, "Bad number in XPM header " + header);
		}
	}

	/**
	 * Read the bitmap part of a XPM image.
	 *
	 * In the TYP file, XPM is used when there is not really an image, so this is not
	 * always called.
	 */
	protected BitmapImage readImage(TokenScanner scanner, ColourInfo colourInfo) {
		StringBuffer sb = new StringBuffer();
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();

		for (int i = 0; i < height; i++) {
			String line = scanner.readLine();
			if (line.charAt(0) != '"' || line.charAt(line.length()-1) != '"')
				throw new SyntaxException(scanner, "xpm bitmap line not surrounded by quotes: " + line);
			sb.append(line.substring(1, line.length() - 1));
		}

		int cpp = colourInfo.getCharsPerPixel();
		if (sb.length() != width * height * cpp) {
			throw new SyntaxException(scanner, "Got " + sb.length() + " of image data, " +
					"expected " + width * height * cpp);
		}

		return new BitmapImage(width, height, cpp, colourInfo, sb.toString());
	}

	protected boolean commonKey(TokenScanner scanner, TypElement current, String name, String value) {
		if (name.equals("Type")) {
			try {
				int ival = Integer.decode(value);
				if (ival > 0x10000) {
					current.setType((ival >> 8) & 0x1ff);
					current.setSubType(ival & 0xff);
				} else {
					current.setType(ival & 0xff);
				}
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number " + value);
			}

		} else if (name.startsWith("String")) {
			try {
				current.addLabel(value);
			} catch (Exception e) {
				throw new SyntaxException(scanner, "Bad number in " + value);
			}

		} else if (name.equals("Xpm")) {
			Xpm xpm = readXpm(scanner, value);
			current.setXpm(xpm);

		} else if (name.equals("FontStyle")) {
			int font = decodeFontStyle(value);
			current.setFontStyle(font);

		} else if (name.equals("CustomColor") || name.equals("ExtendedLabels")) {
			// These are just noise, the appropriate flag is set if any feature is used.

		} else if (name.equals("DaycustomColor")) {
			current.setDayCustomColor(value);

		} else if (name.equals("NightcustomColor")) {
			current.setNightCustomColor(value);

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
	protected Xpm readXpm(TokenScanner scanner, String header) {


		ColourInfo colourInfo = readColourInfo(scanner, header);

		Xpm xpm = new Xpm();
		xpm.setColourInfo(colourInfo);

		int height = colourInfo.getHeight();
		int width = colourInfo.getWidth();
		if (height > 0 && width > 0) {
			colourInfo.setHasBitmap(true);
			BitmapImage image = readImage(scanner, colourInfo);
			xpm.setImage(image);
		}

		return xpm;
	}
}
