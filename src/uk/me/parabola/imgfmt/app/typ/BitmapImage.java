package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Holds a bitmap image for the typ file.
 *
 * There are a number of different formats allowed.
 *
 * Based on code by Thomas Lußnig, but type and colour information separated out and
 * deals with more than just points.
 */
public class BitmapImage implements Image {

	private final ColourInfo colourInfo;
	private final String image;

	public BitmapImage(ColourInfo colourInfo, String image) {
		this.colourInfo = colourInfo;
		this.image = image;
	}

	public void write(ImgFileWriter writer) {

		final int bitSize = colourInfo.getBitsPerPixel();
		int cpp = colourInfo.getCharsPerPixel();

		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();

		int i = 0;
		for (int h = 0; h < height; h++) {
			// Each row is padded to a byte boundary, creating a new bit writer for every
			// row ensures that happens.
			BitWriter bitWriter = new BitWriter();

			for (int w = 0; w < width; w++) {
				String idx = image.substring(i, i + cpp);
				i += cpp;

				int val = colourInfo.getIndex(idx);
				bitWriter.putn(val, bitSize);
			}
			writer.put(bitWriter.getBytes(), 0, bitWriter.getLength());
		}
	}

	public int compare(BitmapImage a, BitmapImage b) {
		throw new UnsupportedOperationException();
		//if (a == null)
		//	return 1;
		//if (b == null)
		//	return -1;
		//if (a.typ < b.typ)
		//	return -1;
		//if (a.typ > b.typ)
		//	return 1;
		//if (a.dayNight < b.dayNight)
		//	return -1;
		//if (a.dayNight > b.dayNight)
		//	return 1;
		//return 0;
	}

}
