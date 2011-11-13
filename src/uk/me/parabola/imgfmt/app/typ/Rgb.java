package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

public class Rgb {
	private final int b;
	private final int g;
	private final int r;
	private final int a;

	public Rgb(int r, int g, int b, int a) {
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	public Rgb(int r, int g, int b) {
		this(r, g, b, 0xff);
	}

	/**
	 * Initialise from a string.
	 *
	 * The format is #RRGGBB and without the '#'. You can also append
	 * an alpha value. FF for fully opaque, and 00 for fully transparent.
	 * The typ file only deals with fully transparent.
	 *
	 * @param in The string form of the color.
	 */
	public Rgb(String in) {
		String colour = in;
		if (colour.startsWith("#"))
			colour = colour.substring(1);

		r = Integer.parseInt(colour.substring(0, 2), 16);
		g = Integer.parseInt(colour.substring(2, 4), 16);
		b = Integer.parseInt(colour.substring(4, 6), 16);
		if (colour.length() > 6)
			a = Integer.parseInt(colour.substring(6, 8), 16);
		else
			a = 0xff;
	}

	/**
	 * Create a new Rgb from the given one, adding the given alpha channel value.
	 */
	public Rgb(Rgb rgb, int alpha) {
		this(rgb.r, rgb.g, rgb.b, alpha);
	}

	public void write(ImgFileWriter writer, byte type) {
		if (type != 0x10)
			throw new FormatException("Invalid color deep");
		writer.put((byte) b);
		writer.put((byte) g);
		writer.put((byte) r);
	}

	public boolean isTransparent() {
		return a == 0;
	}

	public String toString() {
		if (a == 0xff)
			return String.format("#%02x%02x%02x", r, g, b);
		else
			return String.format("#%02x%02x%02x%02x", r, g, b, a);
	}

	public int getB() {
		return b;
	}

	public int getG() {
		return g;
	}

	public int getR() {
		return r;
	}

	public int getA() {
		return a;
	}
}
