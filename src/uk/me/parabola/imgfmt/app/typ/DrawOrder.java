package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Writeable;

/**
 * The drawing order for a type and a set of subtypes.
 *
 * The drawing order is specified by the order of these within the file, rather than anything
 * actually in the item.
 */
public class DrawOrder implements Writeable {
	private final byte typ;
	private int subTypes;

	public DrawOrder(int typ) {
		this.typ = (byte) typ;
	}

	public void write(ImgFileWriter writer) {
		writer.put(typ);
		writer.putInt(subTypes);
	}

	public void addSubtype(int subtype) {
		if (subtype > 0)
			subTypes |= 1 << subtype;
	}
}
