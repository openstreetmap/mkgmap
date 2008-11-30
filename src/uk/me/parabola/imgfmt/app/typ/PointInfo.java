package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

public class PointInfo {
	final BitmapImage bitmap;

	public PointInfo(BitmapImage bitmap) {
		this.bitmap = bitmap;
	}

	public void write(ImgFileWriter writer, int maxOffset) {
		char wtype = (char) (bitmap.getSubtype() | bitmap.getTyp() << 5);
		writer.putChar(wtype);

		if (maxOffset < 0x100)
			writer.put((byte) bitmap.getOffset());
		else
			writer.putChar((char) bitmap.getOffset());
	}
}
