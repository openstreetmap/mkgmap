package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

public class Rgb {
	private int idx;
	public final int b;
	public final int g;
	public final int r;

	public Rgb(int r, int g, int b, int i) {
		this.b = b;
		this.g = g;
		this.r = r;
		idx = i;
	}

	public Rgb(Rgb rgb, byte idx) {
		b = rgb.b;
		g = rgb.g;
		r = rgb.r;
		this.idx = idx;
	}

	public void write(ImgFileWriter writer, byte type) {
		if (type != 0x10)
			throw new FormatException("Invalid color deep");
		writer.put((byte) b);
		writer.put((byte) g);
		writer.put((byte) r);
	}

	int getIdx() {
		return idx;
	}

	void setIdx(int idx) {
		this.idx = idx;
	}
}
