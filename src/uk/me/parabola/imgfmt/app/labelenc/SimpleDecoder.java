package uk.me.parabola.imgfmt.app.labelenc;

import java.io.ByteArrayOutputStream;

/**
 * Created by IntelliJ IDEA. User: steve Date: Dec 23, 2007 Time: 1:43:07 PM To
 * change this template use File | Settings | File Templates.
 */
public class SimpleDecoder implements CharacterDecoder {
	private ByteArrayOutputStream out = new ByteArrayOutputStream();

	public boolean addByte(int b) {
		if (b == 0) {
			out.reset();
			return true;
		}

		out.write(b);

		return false;
	}

	public EncodedText getText() {
		byte[] ba = out.toByteArray();
		return new EncodedText(ba, ba.length);
	}
}
