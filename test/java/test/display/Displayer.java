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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays data in a manner similar to imgdecode written by John Mechalas.
 *
 * So we have an address on the left, undecoded bytes in the middle and
 * the decoded text and explination on the right.
 *
 * @author Steve Ratcliffe
 */
public class Displayer {
	private static final String SEPARATOR = "---------------------------------"
			+ "---------------------------------------------------------------"
			+ "---------------------------------------------------------------"
			;
	private static final int TABLE_WIDTH = 80;

	private String title;
	private List<DisplayItem> items = new ArrayList<DisplayItem>();

	private ReadStrategy reader;

	public Displayer(ReadStrategy reader) {
		this.reader = reader;
	}

	/**
	 * Prints this displayer, and causes all the contained display items to
	 * be printed out.
	 * @param writer The stream to write to.
	 */
	public void print(PrintStream writer) {
		printTitle(writer);
		for (DisplayItem item : items) {
			item.print(writer);
		}
	}

	private void printTitle(PrintStream writer) {
		if (title == null)
			return;

		int leadin = 9;
		writer.printf("%s ", SEPARATOR.substring(0, leadin));
		writer.print(title);
		writer.printf(" %s", SEPARATOR.substring(0, TABLE_WIDTH - leadin - title.length() - 2));
		writer.println();
	}

	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Create a display item for the current position.  You can add data and
	 * lines of text to it.  If you can its easier to use the convenience
	 * routines below.
	 *
	 * This must be called *before* getting any data from the reader as it
	 * records the file postition.
	 *
	 * @return A display item.
	 */
	public DisplayItem item() {
		DisplayItem item = new DisplayItem();
		item.setStartPos(reader.position());

		items.add(item);
		return item;
	}

	/**
	 * Make a gap in the display, nothing will be printed apart from the
	 * separators.
	 */
	public void gap() {
		DisplayItem item = new DisplayItem();
		item.addText(" ");
		items.add(item);
	}

	public byte byteValue(String text) {
		DisplayItem item = item();
		int val = item.setBytes(reader.get());
		item.addText(text, val);
		return (byte) val;
	}

	public char charValue(String text) {
		DisplayItem item = item();
		int val = item.setBytes(reader.getChar());
		item.addText(text, val);
		return (char) val;
	}

	public int intValue(String text) {
		DisplayItem item = item();
		int val = item.setBytes(reader.getInt());
		item.addText(text, val);
		return val;
	}

	public int int3Value(String text) {
		DisplayItem item = item();
		int val = item.setBytes3(reader.get3());
		item.addText(text, (val & 0xffffff));
		return val;
	}

	public void rawValue(int n, String text) {
		DisplayItem item = item();
		item.setBytes(reader.get(n));
		item.addText(text);
	}

	public String stringValue(int n, String text) {
		DisplayItem item = item();
		byte[] b = item.setBytes(reader.get(n));
		String val = new String(b);
		item.addText(text, val);
		return val;
	}

	public String zstringValue(String text) {
		DisplayItem item = item();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte b;
		while ((b = reader.get()) != '\0')
			os.write(b);
		String val = os.toString();
		os.write('\0');
		item.setBytes(os.toByteArray());
		item.addText(text, val);
		return val;
	}
}
