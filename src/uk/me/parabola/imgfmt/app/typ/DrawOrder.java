package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Writeable;

public class DrawOrder implements Writeable {
	private final char typ;
	private final char unk1;
	private final byte unk2;

	public DrawOrder(char typ, char unk1, byte unk2) {
		this.typ = typ;
		this.unk1 = unk1;
		this.unk2 = unk2;
	}

	public void write(ImgFileWriter writer) {
		writer.putChar(typ);
		writer.putChar(unk1);
		writer.put(unk2);
	}
}
