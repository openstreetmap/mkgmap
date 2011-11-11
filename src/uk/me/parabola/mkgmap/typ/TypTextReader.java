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

import uk.me.parabola.imgfmt.app.typ.ShapeStacking;
import uk.me.parabola.imgfmt.app.typ.TYPFile;
import uk.me.parabola.imgfmt.app.typ.TypParam;
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

	private final TypParam param = new TypParam();
	private final ShapeStacking stacking = new ShapeStacking();

	public void read(String filename, Reader r) {
		TokenScanner scanner = new TokenScanner(filename, r);

		ProcessMethod currentSection = null;

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
					ProcessMethod newMethod = readSectionType(scanner);
					if (currentSection != null)
						currentSection.finish();
					currentSection = newMethod;
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

	private ProcessMethod readSectionType(TokenScanner scanner) {
		String sectionName = scanner.nextValue().toLowerCase();
		scanner.validateNext("]"); // Check for closing bracket

		if ("_id".equals(sectionName)) {
			return new ProcessMethod() {
				public void processLine(TokenScanner scanner, String name, String value) {
					idSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("_draworder".equals(sectionName)) {
			return new ProcessMethod() {
				public void processLine(TokenScanner scanner, String name, String value) {
					drawOrderSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("_point".equals(sectionName)) {
			return new ProcessMethod() {
				public void processLine(TokenScanner scanner, String name, String value) {
					pointSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("_line".equals(sectionName)) {
			return new ProcessMethod() {
				public void processLine(TokenScanner scanner, String name, String value) {
					lineSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("_polygon".equals(sectionName)) {
			return new ProcessMethod() {
				public void processLine(TokenScanner scanner, String name, String value) {
					polygonSection(scanner, name, value);
				}

				public void finish() {
				}
			};

		} else if ("end".equals(sectionName)) {
			return null;
		}

		throw new SyntaxException(scanner, "Unrecognised section name: " + sectionName);
	}

	private void idSection(TokenScanner scanner, String name, String value) {
		int ival;
		try {
			ival = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new SyntaxException(scanner, "Bad integer " + value);
		}

		if (name.equals("FID")) {
			param.setFamilyId(ival);
		} else if (name.equals("ProductCode")) {
			param.setProductId(ival);
		} else if (name.equals("CodePage")) {
			param.setCodePage(ival);
		} else {
			throw new SyntaxException(scanner, "Unrecognised keyword in id section: " + name);
		}

	}

	private void drawOrderSection(TokenScanner scanner, String name, String value) {
		if (!name.equals("Type"))
			throw new SyntaxException(scanner, "Unrecognised keyword in draw order section: " + name);

		String[] foo = value.split(",");
		if (foo.length != 2)
			throw new SyntaxException(scanner, "Unrecognised drawOrder type " + value);

		int fulltype = Integer.decode(foo[0]);
		int type;
		int subtype = 0;

		if (fulltype > 0x10000) {
			type = (fulltype >> 8) & 0xff;
			subtype = fulltype & 0xff;
		} else {
			type = fulltype & 0xff;
		}

		int level = Integer.parseInt(foo[1]);
		stacking.addPolygon(level, type, subtype);
	}

	private void pointSection(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, name, value))
			return;
	}

	private void lineSection(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, name, value))
			return;
	}

	private void polygonSection(TokenScanner scanner, String name, String value) {
		if (commonKey(scanner, name, value))
			return;
	}

	private boolean commonKey(TokenScanner scanner, String name, String value) {
		return false;
	}

	public TypParam getParam() {
		return param;
	}

	public ShapeStacking getStacking() {
		return stacking;
	}

	interface ProcessMethod {
		public void processLine(TokenScanner scanner, String name, String value);

		public void finish();
	}

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
		typ.setTypParam(tr.param);
		typ.setStacking(tr.stacking);

		typ.write();
		typ.close();
	}
}
