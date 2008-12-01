package uk.me.parabola.imgfmt.app.typ;

import java.util.Comparator;
import java.util.Map;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Writeable;
import uk.me.parabola.log.Logger;

public class BitmapImage implements Writeable, Comparator<BitmapImage> {
	private static final Logger log = Logger.getLogger(BitmapImage.class);

	private int off;
	private byte dayNight;	// 7=Night 1=Day
	private byte width;
	private byte height;
	private String image;
	private byte typ;
	private int cpc;
	private byte subtype;
	private Map<String, Rgb> colors;

	public final byte getTyp() {
		return typ;
	}

	public final byte getSubtype() {
		return subtype;
	}

	private BitmapImage() { /*for compare*/ }

	protected static BitmapImage comperator() {
		return new BitmapImage();
	}

	public BitmapImage(byte typ, byte subtype, byte dayNight, int width,
			Map<String, Rgb> colours, int cpc, String image)
	{
		if (image == null)
			throw new FormatException("NULL Image");
		height = (byte) (image.length() / width);
		colors = colours;
		if (width != 16)
			throw new FormatException("Only 16 pixel with supported");
		if (height * width != image.length())
			throw new FormatException("Only 16 pixel with supported");
		this.cpc = cpc;
		this.dayNight = dayNight;
		this.width = (byte) width;
		this.image = image;
		this.typ = typ;
		this.subtype = subtype;
	}

	public void write(ImgFileWriter writer) {
		off = writer.position();
		byte cc = (byte) (colors.size());
		// We only Support up to 16 Colors(currently)
		writer.put(dayNight);
		writer.put(width);
		writer.put(height);
		writer.put(cc);
		writer.put((byte) 0x10); // 0x10 => 888 (8Bits per Color)
		// 0x20 => 444 (4Bits per Color)
		int cid = 0;
		for (Rgb rgb : colors.values()) {
			rgb.write(writer, (byte) 0x10);
			rgb.setIdx(cid++);
		}
		int idx = 0;
		try {
			if (cc <= 16) {
				for (idx = 0; idx < image.length(); idx += 2 * cpc) {
					int p2 = colors.get(image.substring(idx, idx + cpc)).getIdx();
					int p1 = colors.get(image.substring(idx + 1, idx + 1 + cpc)).getIdx();
					if (p1 == -1 || p2 == -1)
						throw new FormatException("Invalid Color Code");
					byte p = (byte) (p1 << 4 | p2);
					writer.put(p);
				}
			} else {
				for (idx = 0; idx < image.length(); idx += 2) {
					int p = colors.get(image.substring(idx, idx + cpc)).getIdx();
					if (p == -1)
						throw new FormatException("Invalid Color Code");
					writer.put((byte) p);
				}
			}
		}
		catch (Throwable ex) {
			log.error(ex.getMessage(), ex);
			for (Map.Entry<String, Rgb> e : colors.entrySet())
				log.info("'" + e.getKey() + "' c rgb(" + e.getValue().r + " , " + e
						.getValue().g + " , " + e.getValue().b + ")");
			log.info("bild[idx+0]='" + image
					.substring(idx, idx + cpc) + "' => " + colors
					.get(image.substring(idx, idx + cpc)));
			log.info(new StringBuilder().append("bild[idx+1]='").append(image
					.substring(idx, idx + 1 + cpc)).append("' => ").append(colors
					.get(image.substring(idx, idx + 1 + cpc))).toString());
		}
		// TODO String with names
	}

	public int getOffset() {
		return off - TYPHeader.HEADER_LEN;
	}

	public int getSize() {
		return 5 + colors.size() * 3 + width * height / 2;
	}

	public int compare(BitmapImage a, BitmapImage b) {
		if (a == null)
			return 1;
		if (b == null)
			return -1;
		if (a.typ < b.typ)
			return -1;
		if (a.typ > b.typ)
			return 1;
		if (a.dayNight < b.dayNight)
			return -1;
		if (a.dayNight > b.dayNight)
			return 1;
		return 0;
	}
}
