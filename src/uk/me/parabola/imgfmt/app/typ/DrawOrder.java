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
	private final byte type;
	private int subTypes;
	private boolean hasSubtypes;

	public DrawOrder(int type) {
		this.type = (byte) (type & 0xff);
		if (type >= 0x100)
			hasSubtypes = true;
	}

	public void write(ImgFileWriter writer) {
		writer.put(type);
		writer.putInt(subTypes);
	}

	public void addSubtype(int subtype) {
		if (hasSubtypes)
			subTypes |= 1 << subtype;
	}
}
