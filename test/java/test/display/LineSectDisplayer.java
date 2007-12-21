/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Dec 16, 2007
 */
package test.display;

import uk.me.parabola.imgfmt.app.ReadStrategy;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Print out the line sections.
 *
 * @author Steve Ratcliffe
 */
public class LineSectDisplayer {
	private ReadStrategy reader;
	private PrintStream outStream;

	private List<Integer> offsets = new ArrayList<Integer>();

	public LineSectDisplayer(ReadStrategy reader, PrintStream outStream) {
		this.reader = reader;
		this.outStream = outStream;
	}

	public void print() {
		Displayer d = new Displayer(reader);
		d.setTitle("Line type styles");

		DisplayItem item = d.item();
		item.addText("A list of line types, and pointers to their styles");

		// Get the types first as they have the offsets into the styles
		// section that we will need later.
		reader.position(0x3d);
		long typestart = reader.getInt();
		int itemsize = reader.getChar();
		int size = reader.getInt();

		// Now get the styles start, as needed
		reader.position(0x1f);
		long stylestart = reader.getInt();

		d = printTypes(typestart, itemsize, size, stylestart);
		d.print(outStream);

		reader.position(0x1f);
		stylestart = reader.getInt();

		size = reader.getInt();
		d = printStyles(stylestart, size);
		d.print(outStream);
	}

	private Displayer printStyles(long stylestart, int size) {
		Displayer d = new Displayer(reader);
		d.setTitle("Line styles");

		reader.position(stylestart);

		// As we don't really know the format, we have to go to an effort to
		// work out the sizes the data.
		Map<Integer, Integer> sizes = calcSizes(size);

		// Actually print out the styles the best we can
		for (Map.Entry<Integer, Integer> ent : sizes.entrySet()) {
			long pos = stylestart + ent.getKey();
			reader.position(pos);
			int totalSize = ent.getValue();

			// The first two bytes appear to determine what comes next
			int flags = d.charValue("Flags %04x");
			totalSize -= 2;

			// If the bottom byte is clear then it is straight forward.
			DisplayItem item;
			boolean known;
			if ((flags & 0xfe) == 0) {
				known = true;
				d.int3Value("Foreground %06x");
				d.int3Value("Border %06x");
				totalSize -= 6;

				if ((flags & 0x1) == 0x1) {
					d.int3Value("Foreground %06x");
					d.int3Value("Border %06x");
					totalSize -= 6;
				}

				byte width = d.byteValue("Line width %d");

				item = d.item();
				int total = item.setBytes(reader.get());
				Formatter fmt = new Formatter();
				item.addText(fmt.format("border width %3.1f", (total-width)/2.0).toString());
				totalSize -= 2;
			} else {
				// else it isn't :)

				// Always does appear to be two colours follow, although second
				// doesn't do anything.
				d.int3Value("Foreground %06x");
				d.int3Value("Background %06x");
				totalSize -= 6;
				// There is then a bit sequence that defines the icon, but I've
				// not worked out how to get the size.

				// Some empirical entries
				int pp = flags & 0xff;
				known = true;
				switch (pp) {
				case 0x01:
					// 1x24 not bitmap
					totalSize = tmpPrintUnknown(d, totalSize, 8);
					break;
				case 0x0f: // 1x32
					totalSize = tmpPrintUnknown(d, totalSize, 4);
					break;
				case 0x13:
					totalSize = tmpPrintUnknown(d, totalSize, 11);
					break;
				case 0x17:
					totalSize = tmpPrintUnknown(d, totalSize, 8);
					break;
				case 0x1b:
					totalSize = tmpPrintUnknown(d, totalSize, 15);
					break;
				case 0x21: // 4x32 with two more colours, and background colour is used
					totalSize = tmpPrintUnknown(d, totalSize, 22);
					break;
				case 0x23:
					totalSize = tmpPrintUnknown(d, totalSize, 19);
					break;
				case 0x27:
					totalSize = tmpPrintUnknown(d, totalSize, 16);
					break;
				case 0x2f: // 5x32
					totalSize = tmpPrintUnknown(d, totalSize, 20);
					break;
				default:
					known = false;
					break;
				}
			}


			// This seems solid, as long as we have worked out the size of the
			// previous section, which we don't ...
			if ((flags & 0x0100) != 0 && known) {
				d.byteValue("???");
				d.byteValue("Lang %d");
				String s = d.zstringValue("Label: %s");
				totalSize -= 2 + s.length() + 1;
			}

			// Now display everything that is left
			assert totalSize >= 0;
			if (totalSize > 0) {
				item = d.item();
				byte[] b = reader.get(totalSize);
				item.setBytes(b);
				item.addText("???");
			}

			d.gap();
		}
		return d;
	}

	private int tmpPrintUnknown(Displayer d, int totalSize, int n) {
		d.rawValue(n, "code " + n + " unknown");
		return totalSize - n;
	}

	private Map<Integer, Integer> calcSizes(int size) {
		Map<Integer, Integer> sizes = new LinkedHashMap<Integer, Integer>();
		for (int i = 0; i < offsets.size(); i++) {
			int start = offsets.get(i);
			long next;
			if (i == offsets.size() - 1)
				next = size;
			else
				next = offsets.get(i + 1);

			int stylesize = (int) (next - start);
			sizes.put(start, stylesize);
		}
		return sizes;
	}

	private Displayer printTypes(long typestart, int itemsize, int size, long stylestart) {
		Displayer d = new Displayer(reader);
		d.setTitle("Line types");

		reader.position(typestart);

		// These things really can and are different sizes.
		long end = typestart + size;
		for (long pos = typestart; pos < end; pos += itemsize) {
			DisplayItem item = d.item();

			byte[] b = reader.get(itemsize);
			item.setBytes(b);

			// The line type is found by reading the first two byte in little-endian
			// order and then extracting from there.
			int type = (b[0] & 0xff | ((b[1] & 0xff) << 8)) >> 5;
			item.addText("Line type %#x", type);

			// Get the offset into the line style section.  This is the
			// third byte and the fourth if there is one.
			int off = b[2] & 0xff;
			if (b.length > 3)
				off += (b[3] & 0xff) << 8;

			offsets.add(off);
			item.addText("Style at %#x", (int) (stylestart+off));
		}

		return d;
	}
}
