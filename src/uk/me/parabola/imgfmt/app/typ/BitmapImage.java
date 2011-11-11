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

	private final byte width;
	private final byte height;
	private final int cpp;

	private final ColourInfo colourInfo;
	private final String image;

	public BitmapImage(int width, int height, int cpp, ColourInfo colourInfo, String image) {
		this.width = (byte) width;
		this.height = (byte) height;
		this.cpp = cpp;
		this.colourInfo = colourInfo;
		this.image = image;
	}

	public void write(ImgFileWriter writer) {
		//throw new UnsupportedOperationException();
		BitWriter bitWriter = new BitWriter();

		final int bitSize = 1; // TODO will vary for other types
		int len = image.length();
		for (int i = 0; i < len; i += cpp) {
			String idx = image.substring(i, i + cpp);

			// XXX why invert?
			int val = ~colourInfo.getIndex(idx);
			bitWriter.putn(val, bitSize);
		}

		writer.put(bitWriter.getBytes(), 0, bitWriter.getLength());
	}

	// This will be correct for points
	//public void write(ImgFileWriter writer) {
	//	off = writer.position();
	//	byte cc = (byte) (colors.size());
	//	// We only Support up to 16 Colors(currently)
	//	writer.put(dayNight);
	//	writer.put(width);
	//	writer.put(height);
	//	writer.put(cc);
	//	writer.put((byte) 0x10); // 0x10 => 888 (8Bits per Color)
	//	// 0x20 => 444 (4Bits per Color)
	//	int cid = 0;
	//	for (Rgb rgb : colors.values()) {
	//		rgb.write(writer, (byte) 0x10);
	//		//rgb.setIdx(cid++);
	//	}
	//	int idx = 0;
	//	try {
	//		if (cc <= 16) {
	//			for (idx = 0; idx < image.length(); idx += 2 * cpp) {
	//				//int p2 = colors.get(image.substring(idx, idx + cpp)).getIdx();
	//				//int p1 = colors.get(image.substring(idx + 1, idx + 1 + cpp)).getIdx();
	//				//if (p1 == -1 || p2 == -1)
	//				//	throw new FormatException("Invalid Color Code");
	//				//byte p = (byte) (p1 << 4 | p2);
	//				//writer.put(p);
	//			}
	//		} else {
	//			for (idx = 0; idx < image.length(); idx += 2) {
	//				//int p = colors.get(image.substring(idx, idx + cpp)).getIdx();
	//				//if (p == -1)
	//				//	throw new FormatException("Invalid Color Code");
	//				//writer.put((byte) p);
	//			}
	//		}
	//	}
	//	catch (Throwable ex) {
	//		log.error(ex.getMessage(), ex);
	//	}
	//	// TODO String with names
	//}

	public int getOffset() {
		throw new UnsupportedOperationException();
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

	public byte getHeight() {
		return height;
	}
}
