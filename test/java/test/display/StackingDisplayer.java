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
import java.util.Formatter;

/**
 * Print out the Polygon stacking order.  Not fully understood, may be the
 * other way round for example.
 *
 * @author Steve Ratcliffe
 */
public class StackingDisplayer {
	private ReadStrategy reader;
	private PrintStream outStream;

	public StackingDisplayer(ReadStrategy reader, PrintStream outStream) {
		this.reader = reader;
		this.outStream = outStream;
	}

	public void print() {
		Displayer d = new Displayer(reader);
		d.setTitle("Polygon stacking order");

		// Find the details of the section
		reader.position(0x51);
		long start = reader.getInt();
		int itemsize = reader.getChar();
		int size = reader.getInt();

		d = printOrder(start, itemsize, size);
		d.print(outStream);
	}

	private Displayer printOrder(long start, int itemsize, int size) {
		Displayer d = new Displayer(reader);
		d.setTitle("Polygon stacking order");

		reader.position(start);

		int level = 0;
		for (long pos = start; pos < start + size; pos += itemsize) {
			DisplayItem item = d.item();

			byte type = reader.get();
			item.setBytes(type);

			if (type == 0) {
				item.addText("New level %d", ++level);
			} else {
				Formatter fmt = new Formatter();
				fmt.format("Type %02x at level %d", type, level);
				item.addText(fmt.toString());
			}

			d.rawValue(4, "???");
		}
		return d;
	}
}