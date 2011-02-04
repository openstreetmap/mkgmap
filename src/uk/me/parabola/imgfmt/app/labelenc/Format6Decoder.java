package uk.me.parabola.imgfmt.app.labelenc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Convert the 6-bit label format back to a java string.
 */
public class Format6Decoder implements CharacterDecoder {
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private boolean needReset;

	private boolean symbol;
	private boolean lowerCaseOrSeparator;

	private int store;
	private int nbits;

	private final Charset charset = Charset.forName("ascii");

	public boolean addByte(int in) {
		int b = 0xff & in; //wipe out high bits (in case of negative byte)

		if (needReset) {
			needReset = false;
			out.reset();
		}

		store <<= 8;
		store |= b;
		nbits += 8;

		while (nbits >= 6) {
			convertChar((store >> (nbits-6)) & 0x3f);

			if (needReset) {
				// Skip until the next byte boundary.  Note that may mean that
				// we skip more or *less* than 6 bits.
				if (nbits > 8)
					nbits = 8;
				else
					nbits = 0;

				break;
			} else
				nbits -= 6;
		}

		return needReset;
	}

	public DecodedText getText() {
		byte[] ba = out.toByteArray();
		DecodedText text = new DecodedText(ba, charset);

		assert nbits == 0 || nbits == 8;
		// If there is a byte left inside the decoder then we have to let our
		// caller know, so that they can adjust the offset of the next label
		// appropriately.
		if (nbits == 8)
			text.setOffsetAdjustment(-1);
		return text;
	}

	public void reset() {
		needReset = false;
		out.reset();
		store = 0;
		nbits = 0;
	}


	/**
	 * Convert a single 6 bit quantity into a character.
	 * @param b The six bit int.
	 */
	private void convertChar(int b) {
		if (b > 0x2f) {
			needReset = true;
			return;
		}

		char c;

		if (symbol) {
			symbol = false;
			c = Format6Encoder.SYMBOLS.charAt(b);
		}
		else if(lowerCaseOrSeparator) {
			lowerCaseOrSeparator = false;
			if(b == 0x2b || b == 0x2c) {
				c = (char)(b - 0x10); // "thin" separator
			}
			else if(Character.isLetter(b)) {
				// lower case letter
				c = Character.toLowerCase(Format6Encoder.LETTERS.charAt(b));
			}
			else {
				// not a letter so just use as is (could be a digit)
				c = Format6Encoder.LETTERS.charAt(b);
			}
		}
		else {
			switch(b) {
			case 0x1B:
				// next char is lower case or a separator
				lowerCaseOrSeparator = true;
				return;

			case 0x1C:
				// next char is symbol
				symbol = true;
				return;

			case 0x1D:
			case 0x1E:
			case 0x1F:
				// these are separators - use as is
				c = (char)b;
				break;

			default:
				c = Format6Encoder.LETTERS.charAt(b);
				break;
			}
		}
		out.write(c);
	}
}
