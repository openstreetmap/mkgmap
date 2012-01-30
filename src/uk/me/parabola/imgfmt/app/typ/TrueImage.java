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

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * A true colour image.
 *
 * The image is represented by an array of int, with each int in RGBA format.
 *
 * @author Steve Ratcliffe
 */
public class TrueImage implements Image {
	private final ColourInfo colourInfo;
	private final int[] image;
	
	// If this is mode 16, then the transparent colour is set.
	private int transparentPixel;

	public TrueImage(ColourInfo colourInfo, int[] image) {
		analyzeColours(image, colourInfo);
		this.colourInfo = colourInfo;
		this.image = image;
	}

	/**
	 * Write out the image. It is a set of pixel values that are full RGB values, rather than
	 * table driven as in the other image type. If the colour mode is 32 the colours have an
	 * extra 4 bit opacity value following.
	 *
	 * If the colour mode is 16, then the transparent pixel is written just before the image
	 * itself.
	 */
	public void write(ImgFileWriter writer) {
		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();

		int mode = colourInfo.getColourMode();

		// For mode 16, the transparent pixel precedes the pixmap data.
		if (mode == 16) {
			writer.put((byte) (transparentPixel>>8));
			writer.put((byte) (transparentPixel>>16));
			writer.put((byte) (transparentPixel>>24));
		}

		boolean hasAlpha = mode == 32;

		// Unlike the xpm based images, the true-colour image format does not appear to
		// have any padding so write as a continuous block.
		BitWriter bitWriter = new BitWriter();
		for (int h = 0; h < height; h++) {

			for (int w = 0; w < width; w++) {
				int col = image[h * width + w];

				bitWriter.putn(col>>8 & 0xff, 8);
				bitWriter.putn(col>>16 & 0xff, 8);
				bitWriter.putn(col>>24 & 0xff, 8);

				if (hasAlpha) {
					int alpha = 0xff - (col & 0xff);
					alpha = ColourInfo.alphaRound4(alpha);
					bitWriter.putn(alpha, 4);
				}
			}
		}
		writer.put(bitWriter.getBytes(), 0, bitWriter.getLength());
	}

	/**
	 * Analyze the colours and determine if this should be a mode 16 or 32 image.
	 *
	 * By default it will be a mode 0 image. If there is any transparency the appropriate
	 * colour mode will be selected.
	 *
	 * @param image An images as an array of integers. Each integer is a colour in RGBA format.
	 * @param colourInfo The colour mode will be set in this.
	 */
	private void analyzeColours(int[] image, ColourInfo colourInfo) {
		boolean hasTransparent = false;
		boolean hasAlpha = false;

		int nPixels = colourInfo.getWidth() * colourInfo.getHeight();
		for (int i = 0; i < nPixels; i++) {
			int col = image[i];
			int a = col & 0xff;
			if (a == 0) {
				// Completely transparent, change all transparent pixels to the same value
				if (hasTransparent)
					image[i] = transparentPixel;
				else
					transparentPixel = image[i];
				hasTransparent = true;
			} else if (a < 255) {
				// Partially transparent
				hasAlpha = true;
			}
		}

		if (hasAlpha)
			colourInfo.setColourMode(32);
		else if (hasTransparent)
			colourInfo.setColourMode(16);
	}
}
