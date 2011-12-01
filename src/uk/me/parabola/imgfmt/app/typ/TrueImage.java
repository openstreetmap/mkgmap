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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A true colour image.
 *
 * This represented by an array of int.
 *
 * @author Steve Ratcliffe
 */
public class TrueImage implements Image {
	private final ColourInfo colourInfo;
	private final int[] image;

	public TrueImage(ColourInfo colourInfo, int[] image) {
		this.colourInfo = colourInfo;
		this.image = image;
	}

	public void write(ImgFileWriter writer) {
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();

		int n = width * height;
		for (int count = 0; count < n; count++) {
			int col = image[count];
			writer.put((byte) ((col>>8) & 0xff));
			writer.put((byte) ((col>>16) & 0xff));
			writer.put((byte) ((col>>24) & 0xff));
		}
	}
}
