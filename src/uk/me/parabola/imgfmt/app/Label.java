/*
 * Copyright (C) 2006,2014 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 10-Jan-2007
 */
package uk.me.parabola.imgfmt.app;

import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.labelenc.EncodedText;

/**
 * Labels are used for names of roads, points of interest etc.
 *
 * There are different storage formats.
 *
 * 1. A 6 bit compact uppercase ascii format, that has escape codes for some
 * special characters.
 *
 * 2. An 8 bit format.  This seems to be a fairly straightforward latin-1 like
 * encoding with no tricks to reduce the amount of space required.
 *
 * 3. A multi-byte format. For unicode, cp932 etc.
 *
 * @author Steve Ratcliffe
 */
public class Label {
	public static final Label NULL_LABEL = new Label("");
	public static final Label NULL_OUT_LABEL = new Label(new char[0]);

	private final String text;
	private final char[] encText;

	// The offset in to the data section.
	private int offset;

	public Label(String text) {
		this.text = text;
		this.encText = null;
	}

	public Label(char[] encText) {
		this.encText = encText;
		this.text = null;
	}

	public int getLength() {
		if (text != null)
			return text.length();
		if (encText != null)
			return encText.length;
		return 0;
	}

	public String getText() {
		assert text != null;
		return text;
	}

	public char[] getEncText() {
		return encText;
	}

	// highway shields and "thin" separators
	public final static Pattern SHIELDS = Pattern.compile("[\u0001-\u0006\u001b-\u001c]");

	// "fat" separators
	private final static Pattern SEPARATORS = Pattern.compile("[\u001d-\u001f]");

	// two or more whitespace characters
	private final static Pattern SQUASH_SPACES = Pattern.compile("\\s\\s+");

	public static String stripGarminCodes(String s) {
		if(s == null)
			return null;
		s = SHIELDS.matcher(s).replaceAll(""); // remove
		s = SEPARATORS.matcher(s).replaceAll(" "); // replace with a space
		s = SQUASH_SPACES.matcher(s).replaceAll(" "); // replace with a space
		// a leading separator would have turned into a space so trim it
		return s.trim();
	}

	public static String squashSpaces(String s) {
		if(s == null || s.isEmpty())
			return null;
		return SQUASH_SPACES.matcher(s.trim()).replaceAll(" "); // replace with single space
	}

	/**
	 * The offset of this label in the LBL file.  The first byte of this file
	 * is zero and an offset of zero means that the label has a zero length/is
	 * empty.
	 *
	 * @return The offset within the LBL file of this string.
	 */
	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 * Write this label to the given img file.
	 *
	 * @param writer The LBL file to write to.
	 * @param encText The encoded version of the text for this label.
	 */
	public void write(ImgFileWriter writer, EncodedText encText) {
		assert encText != null;

		if (encText.getLength() > 0)
			writer.put(encText.getCtext(), 0, encText.getLength());
	}

	/**
	 * String version of the label, for diagnostic purposes.
	 */
	public String toString() {
		return text != null ? text : "[" + offset + "]";
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return offset == ((Label) o).offset;
	}

	public int hashCode() {
		return offset;
	}
}
