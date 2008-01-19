package uk.me.parabola.imgfmt.app.labelenc;

import java.io.ByteArrayOutputStream;

public class Format6Decoder implements CharacterDecoder {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private boolean needReset;

	private boolean symbol = false;
	private boolean lower = false;

	private short store = 0;
	private int offset = -6;

	public boolean addByte(int b) {
		b = 0x000000FF & b; //wipe out high bits (in case of negative byte)

		if (needReset) {
			needReset = false;
			out.reset();
			store = 0;
			offset = -6;
		}

		store <<= 8;
		store |= b;
		offset += 8;

		addChar((store >> offset) & 0x3f);
		offset -= 6;

		if (offset >= 0 && !needReset) {
			addChar((store >> offset) & 0x3f);
			offset -= 6;
		}
		
		return needReset;
	}

	private void addChar(int b) {

		if (b > 0x2f) {
			needReset = true;
			return;
		}

		char c;

		if (symbol) {
			symbol = false;
			c = Format6Encoder.symbols.charAt(b);
		} else {
			switch(b) {
				case 0x1B:
					// perhaps this is "next-char lower case"?
					return;
				case 0x1C:
					// next char is symbol
					symbol = true;
					return;
				case 0x1D:
				case 0x1E:
				case 0x1F:
					// these define abbreviations; fall through to
					// lookup which returns a space
				default:
					c = Format6Encoder.letters.charAt(b);
			}
		}
		out.write(c);
	}


	public EncodedText getText() {
		byte[] ba = out.toByteArray();
		return new EncodedText(ba, ba.length);
	}
}
