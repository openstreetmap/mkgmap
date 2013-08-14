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

import uk.me.parabola.imgfmt.app.typ.AlphaAdder;
import uk.me.parabola.imgfmt.app.typ.BitmapImage;
import uk.me.parabola.imgfmt.app.typ.ColourInfo;
import uk.me.parabola.imgfmt.app.typ.Image;
import uk.me.parabola.imgfmt.app.typ.Rgb;
import uk.me.parabola.imgfmt.app.typ.TrueImage;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypElement;
import uk.me.parabola.imgfmt.app.typ.Xpm;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.Token;
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
	private boolean hasXpm;

	protected CommonSection(TypData data) {
		this.data = data;
	}

	/**
	 * Deal with all the keys that are common to the different element types.
	 * Most tags are in fact the same for every element.
	 * 
	 * @return True if this routine has processed the tag.
	 */
	protected boolean commonKey(TokenScanner scanner, TypElement current, String name, String value) {
		if (name.equalsIgnoreCase("Type")) {
			try {
				int ival = Integer.decode(value);
				if (ival >= 0x100) {
					current.setType(ival >>> 8);
					current.setSubType(ival & 0xff);
				} else {
					current.setType(ival & 0xff);
				}
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number " + value);
			}

		} else if (name.equalsIgnoreCase("SubType")) {
			try {
				int ival = Integer.decode(value);
				current.setSubType(ival);
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number for sub type " + value);
			}

		} else if (name.toLowerCase().startsWith("string")) {
			try {
				current.addLabel(value);
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Bad number in " + value);
			}

		} else if (name.equalsIgnoreCase("Xpm")) {
			Xpm xpm = readXpm(scanner, value, current.simpleBitmap());
			current.setXpm(xpm);

		} else if (name.equalsIgnoreCase("FontStyle")) {
			int font = decodeFontStyle(value);
			current.setFontStyle(font);

		} else if (name.equalsIgnoreCase("CustomColor") || name.equals("ExtendedLabels")) {
			// These are just noise, the appropriate flag is set if any feature is used.

		} else if (name.equalsIgnoreCase("DaycustomColor")) {
			current.setDayFontColor(value);

		} else if (name.equalsIgnoreCase("NightcustomColor")) {
			current.setNightCustomColor(value);

		} else if (name.equalsIgnoreCase("Comment")) {
			// a comment that is ignored.
		} else {
			return false;
		}

		return true;
	}

	protected int decodeFontStyle(String value) {
		if (value.startsWith("NoLabel") || value.equalsIgnoreCase("nolabel")) {
			return 1;
		} else if (value.equalsIgnoreCase("SmallFont") || value.equalsIgnoreCase("Small")) {
			return 2;
		} else if (value.equalsIgnoreCase("NormalFont") || value.equalsIgnoreCase("Normal")) {
			return 3;
		} else if (value.equalsIgnoreCase("LargeFont") || value.equalsIgnoreCase("Large")) {
			return 4;
		} else if (value.equalsIgnoreCase("Default")) {
			return 0;
		} else {
			warnUnknown("font value " + value);
			return 0;
		}
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
	 * Read the colour lines from the XPM format image.
	 */
	protected ColourInfo readColourInfo(TokenScanner scanner, String header) {

		ColourInfo colourInfo = new ColourInfo();
		parseXpmHeader(scanner, colourInfo, header);

		for (int i = 0; i < colourInfo.getNumberOfColours(); i++) {
			scanner.validateNext("\"");

			int cpp = colourInfo.getCharsPerPixel();

			Token token = scanner.nextRawToken();
			String colourTag = token.getValue();
			while (colourTag.length() < cpp)
				colourTag += scanner.nextRawToken().getValue();
			colourTag = colourTag.substring(0, cpp);

			scanner.validateNext("c");

			String colour = scanner.nextValue();
			if (colour.charAt(0) == '#') {
				colour = scanner.nextValue();
				colourInfo.addColour(colourTag, new Rgb(colour));
			} else if (colour.equalsIgnoreCase("none")) {
				colourInfo.addTransparent(colourTag);
			} else {
				throw new SyntaxException(scanner, "Unrecognised colour: " + colour);
			}

			scanner.validateNext("\"");

			readExtraColourInfo(scanner, colourInfo);
		}

		return colourInfo;
	}

	/**
	 * Get any keywords that are on the end of the colour line. Must not step
	 * over the new line boundary.
	 */
	private void readExtraColourInfo(TokenScanner scanner, AlphaAdder colour) {
		while (!scanner.isEndOfFile()) {
			Token tok = scanner.nextRawToken();
			if (tok.isEol())
				break;

			String word = tok.getValue();

			// TypWiz uses alpha, TypViewer uses "canalalpha"
			if (word.endsWith("alpha")) {
				scanner.validateNext("=");
				String aval = scanner.nextValue();

				try {
					// Convert to rgba format
					int alpha = Integer.decode(aval);
					alpha = 255 - ((alpha<<4) + alpha);
					colour.addAlpha(alpha);
				} catch (NumberFormatException e) {
					throw new SyntaxException(scanner, "Bad number for alpha value " + aval);
				}

			} // ignore everything we don't recognise.
		}
	}

	/**
	 * Read the bitmap part of a XPM image.
	 *
	 * In the TYP file, XPM is used when there is not really an image, so this is not
	 * always called.
	 *
	 * Almost all of this routine is checking that the strings are valid. They have the
	 * correct length, there are quotes at the beginning and end at that each pixel tag
	 * is listed in the colours section.
	 */
	protected BitmapImage readImage(TokenScanner scanner, ColourInfo colourInfo) {
		StringBuffer sb = new StringBuffer();
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();
		int cpp = colourInfo.getCharsPerPixel();

		for (int i = 0; i < height; i++) {
			String line = scanner.readLine();
			if (line.isEmpty())
				throw new SyntaxException(scanner, "Invalid blank line in bitmap.");

			if (line.charAt(0) != '"')
				throw new SyntaxException(scanner, "xpm bitmap line must start with a quote: " + line);
			if (line.length() < 1 + width * cpp)
				throw new SyntaxException(scanner, "short image line: " + line);

			line = line.substring(1, 1+width*cpp);
			sb.append(line);

			// Do the syntax check, to avoid an error later when we don't have the line number any more
			for (int cidx = 0; cidx < width * cpp; cidx += cpp) {
				String tag = line.substring(cidx, cidx + cpp);
				try {
					colourInfo.getIndex(tag);
				} catch (Exception e) {
					throw new SyntaxException(scanner,
							String.format("Tag '%s' is not one of the defined colour pixels", tag));
				}
			}
		}

		if (sb.length() != width * height * cpp) {
			throw new SyntaxException(scanner, "Got " + sb.length() + " of image data, " +
					"expected " + width * height * cpp);
		}

		return new BitmapImage(colourInfo, sb.toString());
	}


	/**
	 * The true image format is represented by one colour value for each pixel in the
	 * image.
	 *
	 * The colours are on several lines surrounded by double quotes.
	 * <pre>
	 * "#ff9900 #ffaa11 #feab10 #feab10"
	 * "#f79900 #f7aa11 #feab10 #feab20"
	 * ...
	 * </pre>
	 * There can be any number of colours on the same line, and the spaces are not needed.
	 *
	 * Transparency is represented by using RGBA values "#ffeeff00" or by appending alpha=N
	 * to the end of the colour line. If using the 'alpha=N' method, then there can be only one
	 * colour per line (well it is only the last colour value that is affected if more than one).
	 *
	 * <pre>
	 * "#ff8801" alpha=2
	 * </pre>
	 *
	 * The alpha values go from 0 to 15 where 0 is opaque and 15 transparent.
	 */
	private Image readTrueImage(TokenScanner scanner, ColourInfo colourInfo) {
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();
		final int[] image = new int[width * height];

		int nPixels = width * height;

		int count = 0;
		while (count < nPixels) {
			scanner.validateNext("\"");
			count = readTrueImageLine(scanner, image, count);
		}

		if (scanner.checkToken("\"")) {
			// An extra colour, so this is probably meant to be a mode=16 image.
			// Remove the first pixel and shuffle the rest down, unset the alpha
			// on all the transparent pixels.
			int transPixel = image[0];
			for (int i = 1; i < nPixels; i++) {
				int pix = image[i];
				if (pix == transPixel)
					pix &= ~0xff;
				image[i-1] = pix;
			}

			// Add the final pixel
			scanner.validateNext("\"");
			readTrueImageLine(scanner, image, nPixels-1);
		}

		return new TrueImage(colourInfo, image);
	}

	/**
	 * Read a single line of pixel colours.
	 *
	 * There can be one or more colours on the line and the colours are surrounded
	 * by quotes.  The can be trailing attribute that sets the opacity of
	 * the final pixel.
	 */
	private int readTrueImageLine(TokenScanner scanner, final int[] image, int count) {
		do {
			scanner.validateNext("#");
			String col = scanner.nextValue();
			try {
				int val = (int) Long.parseLong(col, 16);
				if (col.length() <= 6)
					val = (val << 8) + 0xff;

				image[count++] = val;
			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Not a valid colour value ");
			}
		} while (scanner.checkToken("#"));
		scanner.validateNext("\"");

		// Look for any trailing alpha=N stuff.
		final int lastColourIndex = count - 1;
		readExtraColourInfo(scanner, new AlphaAdder() {
			/**
			 * Add the alpha value to the last colour that was read in.
			 *
			 * @param alpha A true alpha value ie 0 is transparent, 255 opaque.
			 */
			public void addAlpha(int alpha) {
				image[lastColourIndex] = (image[lastColourIndex] & ~0xff) | (alpha & 0xff);
			}
		});

		return count;
	}

	/**
	 * Read an XMP image from the input scanner.
	 *
	 * Note that this is sometimes used just for colours so need to deal with
	 * different cases.
	 */
	protected Xpm readXpm(TokenScanner scanner, String header, boolean simple) {
		ColourInfo colourInfo = readColourInfo(scanner, header);
		String msg = colourInfo.analyseColours(simple);
		if (msg != null)
			throw new SyntaxException(scanner, msg);

		Xpm xpm = new Xpm();
		xpm.setColourInfo(colourInfo);

		int height = colourInfo.getHeight();
		int width = colourInfo.getWidth();
		if (height > 0 && width > 0) {
			colourInfo.setHasBitmap(true);
			Image image;
			if (colourInfo.getNumberOfColours() == 0)
				image = readTrueImage(scanner, colourInfo);
			else
				image = readImage(scanner, colourInfo);
			xpm.setImage(image);
		}

		hasXpm = true;
		return xpm;
	}

	protected void warnUnknown(String name) {
		if (seen.contains(name))
			return;

		seen.add(name);
		System.out.printf("Warning: tag '%s' not known\n", name);
	}

	protected void validate(TokenScanner scanner) {
		if (!hasXpm)
			throw new SyntaxException(scanner, "No XPM tag in section");
	}
}
