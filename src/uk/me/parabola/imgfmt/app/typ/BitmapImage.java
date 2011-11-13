package uk.me.parabola.imgfmt.app.typ;

import java.util.Comparator;

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Writeable;

/**
 * Holds a bitmap image for the typ file.
 *
 * There are a number of different formats allowed.
 *
 * Based on code by Thomas Lußnig, but type and colour information separated out and
 * deals with more than just points.
 */
public class BitmapImage implements Writeable, Comparator<BitmapImage> {

	private final ColourInfo colourInfo;
	private final String image;

	public BitmapImage(ColourInfo colourInfo, String image) {
		this.colourInfo = colourInfo;
		this.image = image;
	}

	public void write(ImgFileWriter writer) {
		BitWriter bitWriter = new BitWriter();

		final int bitSize = colourInfo.getBitsPerPixel();
		int cpp = colourInfo.getCharsPerPixel();

		int width = colourInfo.getWidth();
		int height = colourInfo.getHeight();
		int adjwidth = (width&1)==1? width+1: width;

		int i = 0;
		for (int h = 0; h < height; h++) {
			for (int w = 0; w < adjwidth; w++) {
				if (w >= width) {
					bitWriter.putn(0, bitSize);
				} else {
					String idx = image.substring(i, i + cpp);
					i += cpp;

					int val = colourInfo.getIndex(idx);
					bitWriter.putn(val, bitSize);
				}
			}
		}

		writer.put(bitWriter.getBytes(), 0, bitWriter.getLength());
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
