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
 * Create date: Dec 18, 2007
 */
package test.display;

import uk.me.parabola.imgfmt.app.ReadStrategy;

import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

/**
 * For displaying points.  We don't really know which is which yet.
 *
 * @author Steve Ratcliffe
 */
public class PolygonDisplayer {
	private ReadStrategy reader;
	private PrintStream out;

	private List<Integer> offsets = new ArrayList<Integer>();

	public PolygonDisplayer(ReadStrategy reader, PrintStream outStream) {
		this.reader = reader;
		this.out = outStream;
	}

	public void print() {
		printPointDefs();
	}

	private void printPointDefs() {
		reader.position(0x47); // sect4
		int start = reader.getInt();
		int itemsize = reader.getChar();
		int size = reader.getInt();

		reader.position(0x27); // sect1
		int stylestart = reader.getInt();

		printTypes(start, itemsize, size, stylestart);

		reader.position(0x27);
		stylestart = reader.getInt();
		size = reader.getInt();

		printStyles(stylestart, size);
	}

	private void printStyles(int start, int size) {
		Displayer d = new Displayer(reader);
		d.setTitle("Polygon styles");

		for (int i = 0; i < offsets.size(); i++) {
			int off = start + offsets.get(i);
			int end = start + ((i < offsets.size() - 1) ? offsets.get(i + 1) : size);
			reader.position(off);

			int n = end - off;
			d.rawValue(n-10, "???");
			d.zstringValue("s: %s");
			d.gap();
		}

		d.print(out);
	}

	private void printTypes(int start, int itemsize, int size, int stylestart) {
		Displayer d = new Displayer(reader);
		d.setTitle("Polygon types");

		reader.position(start);
		long end = start+size;
		for (long pos = start; pos < end; pos += itemsize) {
			DisplayItem item = d.item();

			byte[] b = reader.get(itemsize);
			item.setBytes(b);
			item.addText("unknown %x", (((b[1]&0xff) << 8) + (b[0]&0xff))>>5);

			// Get the offset into the shape style section.  This is the
			// third byte and the fourth if there is one.
			int off = b[2] & 0xff;
			if (b.length > 3)
				off += (b[3] & 0xff) << 8;

			item.addText("Style at %x", stylestart + off);
			offsets.add(off);
		}

		d.print(out);
	}
}