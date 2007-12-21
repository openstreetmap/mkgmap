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

import uk.me.parabola.imgfmt.app.BufferedReadStrategy;
import uk.me.parabola.imgfmt.app.ReadStrategy;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;

/**
 * Standalone program to display the TYP file as it is worked out.
 *
 * The names and lengths of the fields were obtained from the program gpsexplorer
 * by the author identified as 'garminmaploader@yahoo.com' and released
 * under GPL2+.
 *
 * @author Steve Ratcliffe
 */
public class MpsDisplay {
	private ReadStrategy reader;
	private PrintStream outStream = System.out;
	private static long filelen;

	public MpsDisplay(ReadStrategy reader) {
		this.reader = reader;
	}

	private void print() {
		while (reader.position() < filelen) {
			printSection();
		}
	}

	private void printSection() {
		Displayer d = new Displayer(reader);
		d.setTitle("Section");

		DisplayItem item = d.item();
		byte type = reader.get();

		item.setBytes(type);
		switch (type) {
		case 0x46:
			printHeader(d, item, "Product entry");
			printProduct();
			break;
		case 0x4c:
			printHeader(d, item, "Map entry");
			printFile();
			break;
		case 0x55:
			printHeader(d, item, "Unlock entry");
			printBuffer();
			break;
		case 0x56:
			printHeader(d, item, "Map set entry");
			printMapSet();
			break;
		default:
			printHeader(d, item, "Unknown section type");
			break;
		}
	}

	/**
	 * So ok its not much of a header, but print the value separately anyway.
	 * @param d The displayer
	 * @param item The display item that already contains the byte.
	 * @param text Stuff to display, the kind of section.
	 */
	private void printHeader(Displayer d, DisplayItem item, String text) {
		d.setTitle(text);
		item.addText(text + " id byte");
		d.print(outStream);
	}

	private void printProduct() {
		Displayer d = new Displayer(reader);
		d.charValue("Length %d");
		d.intValue("Product %#x");
		d.zstringValue("Description: %s");
		d.print(outStream);
	}

	private void printMapSet() {
		Displayer d = new Displayer(reader);

		d.charValue("The length %d");
		d.zstringValue("Map set name");
		d.byteValue("???");

		d.print(outStream);
	}

	private void printFile() {
		Displayer d = new Displayer(reader);

		d.charValue("The length %d");

		d.intValue("product id %x");
		d.intValue("mapname %d");
		d.zstringValue("type name %s");
		d.zstringValue("map name %s");
		d.zstringValue("area name %s");
		d.intValue("mapname %d");
		d.intValue("???");

		d.print(outStream);
	}

	private void printBuffer() {
		// Not really interested in this section, but needed for completeness.
		// Its not tested as I don't have any maps that need unlocking.
		Displayer d = new Displayer(reader);
		int len = d.intValue("Length");

		// This may not work anyway... untested.
		int count = 0;
		while (count < len) {
			String val = d.zstringValue("String: %s");
			count += val.length() + 1;
		}

		d.print(outStream);
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: typdisplay <filename>");
			System.exit(1);
		}

		String name = args[0];

		RandomAccessFile raf = null;
		try {
			File file = new File(name);
			filelen = file.length();
			raf = new RandomAccessFile(file, "r");
			ImgChannel chan = new FileImgChannel(raf.getChannel());
			BufferedReadStrategy reader = new BufferedReadStrategy(chan);
			MpsDisplay display = new MpsDisplay(reader);
			display.print();
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + name);
			System.exit(1);
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException e) {
					// ok
				}
			}
		}
	}
}