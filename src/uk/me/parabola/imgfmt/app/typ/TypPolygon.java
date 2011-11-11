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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Holds the data for a polygon style.
 *
 * @author Steve Ratcliffe
 */
public class TypPolygon extends TypElement {
	private static final int F_LABEL = 0x10;
	private static final int F_EXTENDED = 0x20;

	private int type;
	private int subType;

	private final List<TypLabel> labels = new ArrayList<TypLabel>();
	private ColourInfo colourInfo;
	private BitmapImage image;

	private int offset;
	private int fontStyle;
	private Rgb dayFontColour;
	private Rgb nightFontColour;

	public void write(ImgFileWriter writer, CharsetEncoder encoder) {
		offset = writer.position();

		int scheme = colourInfo.getColourScheme();
		if (!labels.isEmpty())
			scheme |= F_LABEL;
		if (fontStyle != 0 || dayFontColour != null)
			scheme |= F_EXTENDED;

		writer.put((byte) scheme);

		colourInfo.write(writer);
		if (image != null)
			image.write(writer);

		// The labels have a length byte to show the number of bytes following. There is
		// also a flag in the length. The strings have a language number proceeding them.
		// The strings themselves are null terminated.
		if ((scheme & F_LABEL) != 0) {
			ByteBuffer out = makeLabelBlock(encoder);
			int flag = 1; // XXX What is this?

			// write out the length byte
			byte len = (byte) ((out.position() << 1) + flag);
			writer.put(len);

			// Prepare and write buffer
			out.flip();
			writer.put(out);
		}

		// The extension section hold font style and colour information for the labels.
		if ((scheme & F_EXTENDED) != 0) {
			byte fontExt = (byte) fontStyle;
			if (dayFontColour != null)
				fontExt |= 0x8;

			writer.put(fontExt);

			if (dayFontColour != null)
				dayFontColour.write(writer, (byte) 0x10);
		}
	}

	/**
	 * Make the label block separately as we need its length before we write it out properly.
	 *
	 * @param encoder For encoding the strings as bytes.
	 * @return A byte buffer with position set to the length of the block.
	 */
	private ByteBuffer makeLabelBlock(CharsetEncoder encoder) {
		ByteBuffer out = ByteBuffer.allocate(256);
		for (TypLabel tl : labels) {
			out.put((byte) tl.getLang());
			CharBuffer cb = CharBuffer.wrap(tl.getText());
			try {
				ByteBuffer buffer = encoder.encode(cb);
				out.put(buffer);
			} catch (CharacterCodingException ignore) {
				System.out.println("WARNING: failed to encode string: " + tl.getText() +
						". File should be in unicode");
			}
			out.put((byte) 0);
		}

		return out;
	}

	public void setType(int type) {
		this.type = type;
	}

	public void setSubType(int subType) {
		this.subType = subType;
	}

	public void addLabel(String text) {
		labels.add(new TypLabel(text));
	}

	public void setColourInfo(ColourInfo colourInfo) {
		this.colourInfo = colourInfo;
	}

	public void setImage(BitmapImage image) {
		this.image = image;
	}

	public int getType() {
		return type;
	}

	public int getSubType() {
		return subType;
	}

	public int getOffset() {
		return offset;
	}

	public void setFontStyle(int font) {
		this.fontStyle = font;
	}

	public void setDayCustomColor(String value) {
		dayFontColour = new Rgb(value);
	}
	public void setNightCustomColor(String value) {
		nightFontColour = new Rgb(value);
	}
}
