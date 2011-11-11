/*
 * Copyright (C) 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.typ;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.channels.FileChannel;

import uk.me.parabola.imgfmt.app.typ.TYPFile;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read in a TYP file in the text format.
 *
 * @author Steve Ratcliffe
 */
public class TypTextReader {

	// As the file is read in, the information is saved into this data structure.
	private final TypData data = new TypData();

	public void read(String filename, Reader r) {
		TokenScanner scanner = new TokenScanner(filename, r);

		ProcessSection currentSection = null;

		while (!scanner.isEndOfFile()) {
			Token tok = scanner.nextToken();

			// We deal with whole line comments here
			if (tok.isValue(";")) {
				scanner.skipLine();
				continue;
			}

			if (tok.getType() == TokType.SYMBOL) {
				switch (tok.getValue().charAt(0)) {
				case ';':
					scanner.skipLine();
					break;
				case '[':
					ProcessSection newSection = readSectionType(scanner);
					if (currentSection != null)
						currentSection.finish();
					currentSection = newSection;
					break;
				case '"':
					scanner.skipLine();
					break;
				}
			} else {
				// Line inside a section
				String name = tok.getValue();

				String sep = scanner.nextValue();
				if (!sep.equals("=") && !sep.equals(":"))
					throw new SyntaxException(scanner, "Expecting '=' or ':' instead of " + sep);

				String value = scanner.readLine();

				if (currentSection == null)
					throw new SyntaxException(scanner, "Missing section start");

				currentSection.processLine(scanner, name, value);
			}
			scanner.skipSpace();
		}
	}

	private ProcessSection readSectionType(TokenScanner scanner) {
		String sectionName = scanner.nextValue().toLowerCase();
		scanner.validateNext("]"); // Check for closing bracket

		if ("end".equals(sectionName)) {
			return null;

		} else if ("_point".equals(sectionName)) {
			return new ProcessSection() {
				public void processLine(TokenScanner scanner, String name, String value) {
					pointSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("_line".equals(sectionName)) {
			return new LineSection(data);

		} else if ("_polygon".equals(sectionName)) {
			return new PolygonSection(data);
		} else if ("_draworder".equals(sectionName)) {
			return new DrawOrderSection(data);
		} else if ("_id".equals(sectionName)) {
			return new IdSection(data);
		}

		throw new SyntaxException(scanner, "Unrecognised section name: " + sectionName);
	}

	private void pointSection(TokenScanner scanner, String name, String value) {
		//if (commonKey(scanner, name, value))
		//	return;
	}

	public TypData getData() {
		return data;
	}

	/**
	 * Simple main program to demonstrate compiling a typ.txt file.
	 *
	 * Usage: TypTextReader [in-file] [out-file]
	 *
	 * in-file defaults to 'default.txt'
	 * out-file defaults to 'OUT.TYP'
	 *
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) throws IOException {
		String in = "default.txt";
		if (args.length > 0)
			in = args[0];
		String out = "OUT.TYP";
		if (args.length > 1)
			out = args[1];

		Reader r = new BufferedReader(new FileReader(in));

		TypTextReader tr = new TypTextReader();
		try {
			tr.read(in, r);
		} catch (SyntaxException e) {
			System.out.println(e.getMessage());
		}

		RandomAccessFile raf = new RandomAccessFile(out, "rw");
		FileChannel channel = raf.getChannel();
		channel.truncate(0);
		
		FileImgChannel w = new FileImgChannel(channel);
		TYPFile typ = new TYPFile(w);
		typ.setData(tr.data);

		typ.write();
		typ.close();
	}

}
