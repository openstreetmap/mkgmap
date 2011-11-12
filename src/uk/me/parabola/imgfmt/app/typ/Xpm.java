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
 * Holds everything read from an XPM value in the typ txt file.
 *
 * @author Steve Ratcliffe
 */
public class Xpm {
	private ColourInfo colourInfo;
	private BitmapImage image;

	public ColourInfo getColourInfo() {
		return colourInfo;
	}

	public void setColourInfo(ColourInfo colourInfo) {
		this.colourInfo = colourInfo;
	}

	public BitmapImage getImage() {
		return image;
	}

	public void setImage(BitmapImage image) {
		this.image = image;
	}

	public boolean hasImage() {
		return image != null;
	}

	public void writeImage(ImgFileWriter writer) {
		image.write(writer);
	}
}
