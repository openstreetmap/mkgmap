package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

public class PointInfo {
	private final BitmapImage bitmap;
	private int type;
	private int subType;

	public PointInfo(BitmapImage bitmap) {
		this.bitmap = bitmap;
	}

	public void write(ImgFileWriter writer, int maxOffset) {
		char wtype = (char) (subType | type << 5);
		writer.putChar(wtype);

		if (maxOffset < 0x100)
			writer.put((byte) bitmap.getOffset());
		else
			writer.putChar((char) bitmap.getOffset());
	}
}
