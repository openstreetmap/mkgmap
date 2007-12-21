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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * @author Steve Ratcliffe
 */
public class DisplayItem {
	private long startPos;

	private byte[] bytes;
	private int nbytes;

	private int textLines;
	private List<String> texts = new ArrayList<String>();
	private int bytelines;

	public void setStartPos(long startPos) {
		this.startPos = startPos;
	}

	public int setBytes(int in) {
		return setBytes(in, 4);
	}

	public int setBytes(char in) {
		return setBytes(in, 2);
	}

	public int setBytes(byte in) {
		return setBytes(in, 1);
	}

	public int setBytes3(int in) {
		return setBytes(in, 3);
	}

	private int setBytes(int in, int n) {
		nbytes += n;
		bytes = new byte[n];

		for (int i = 0; i < n; i++) {
			bytes[i] = (byte) (in >> (i) * 8 & 0xff);
		}
		return in;
	}

	public void addText(String text) {
		texts.add(text);
		textLines++;
	}

	public void print(PrintStream out) {
		bytelines = nbytes / 8 + 1;
		textLines = texts.size();
		int nlines = Math.max(bytelines, textLines);

		long pos = startPos;

		for (int i = 0; i < nlines; i++, pos+=8) {
			if (pos >= startPos + nbytes)
				out.format("         |");
			else
				out.format("%08x |", pos);
			printBytes(out, i);
			printLines(out, i);
			out.println();
		}
	}

	private void printLines(PrintStream out, int lineno) {
		if (lineno >= textLines)
			return;

		String s = texts.get(lineno);
		if (s != null)
			out.print(s);
	}

	private void printBytes(PrintStream out, int lineno) {
		int pos = lineno * 8;
		for (int i = 0; i < 8; i++, pos++) {
			if (lineno >= bytelines || (8*lineno + i) >= nbytes) {
				out.print("   ");
			} else {
				out.format("%02x ", bytes[pos]);
			}
		}
		out.print('|');
	}

	public void addText(String s, int val) {
		StringBuffer sb = new StringBuffer();
		Formatter fmt = new Formatter(sb);
		fmt.format(s, val);
		texts.add(sb.toString());
	}

	public void addText(String s, String val) {
		StringBuffer sb = new StringBuffer();
		Formatter fmt = new Formatter(sb);
		fmt.format(s, val);
		texts.add(sb.toString());
	}

	public byte[] setBytes(byte[] buf) {
		assert this.bytes == null;
		this.bytes = buf;
		this.nbytes = buf.length;
		return buf;
	}
}
