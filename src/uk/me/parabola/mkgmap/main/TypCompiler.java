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
package uk.me.parabola.mkgmap.main;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.typ.TYPFile;
import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.imgfmt.app.typ.TypLabelException;
import uk.me.parabola.imgfmt.app.typ.TypParam;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.typ.TypTextReader;

/**
 * Standalone program to compile a TYP file from the text format.
 * Simple main program to demonstrate compiling a typ.txt file.
 *
 * Usage: TypTextReader [in-file] [out-file]
 *
 * in-file defaults to 'default.txt'
 * out-file defaults to 'OUT.TYP'
 *
 */
public class TypCompiler implements MapProcessor {

	/**
	 * The integration with mkgmap.
	 *
	 * @param args The options that are in force.
	 * @param filename The input filename.
	 * @return Returns the name of the file that was written. It depends on the family id.
	 */
	public String makeMap(CommandArgs args, String filename) {
		assert filename.toLowerCase().endsWith(".txt");

		CharsetProbe probe = new CharsetProbe();
		String readCharset = probe.probeCharset(filename);

		TypData data;
		try {
			data = compile(filename, readCharset);
		} catch (SyntaxException e) {
			throw new MapFailedException("Compiling TYP txt file: " + e.getMessage());
		} catch (FileNotFoundException e) {
			throw new MapFailedException("Could not open TYP file " + filename + " to read");
		}

		TypParam param = data.getParam();
		if (args != null) {
			int family = args.get("family-id", -1);
			int product = args.get("product-id", -1);
			int cp = args.get("code-page", -1);

			if (family != -1)
				param.setFamilyId(family);
			if (product != -1)
				param.setProductId(product);
			if (cp != -1)
				param.setCodePage(cp);
		}

		String base = args.get("overview-mapname", "osmmap");
		String out = base + ".typ";
		
		try {
			writeTyp(data, out);
		} catch (IOException e) {
			throw new MapFailedException("Error while writing typ file", e);
		}

		return out;
	}

	/**
	 * Read and compile a TYP file, returning the compiled form.
	 * @param filename The input filename.
	 * @param charset The character set to use to read this file. We should have already determined
	 * that this character set is valid and can be used to read the file.
	 * @return The compiled form as a data structure.
	 * @throws FileNotFoundException If the file doesn't exist.
	 * @throws SyntaxException All user correctable problems in the input file.
	 */
	private TypData compile(String filename, String charset) throws FileNotFoundException, SyntaxException {
		TypTextReader tr = new TypTextReader();

		try {
			Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(filename), charset));
			tr.read(filename, r);
		} catch (UnsupportedEncodingException e) {
			// Not likely to happen as we should have already used this character set!
			throw new MapFailedException("Unsupported character set", e);
		}

		return tr.getData();
	}

	/**
	 * Write the type file out from the compiled form to the given name.
	 */
	private void writeTyp(TypData data, String filename) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(filename, "rw");
		FileChannel channel = raf.getChannel();
		channel.truncate(0);

		FileImgChannel w = new FileImgChannel(channel);
		TYPFile typ = new TYPFile(w);
		typ.setData(data);

		typ.write();
		typ.close();
	}

	/**
	 * Simple standalone compiler.
	 *
	 * Usage: TypCompiler [in-file] [out-file]
	 *  in-file defaults to 'default.txt'
	 *  out-file defaults to OUT.TYP
	 */
	public static void main(String[] args) throws IOException {
		String in = "default.txt";
		if (args.length > 0)
			in = args[0];
		String out = "OUT.TYP";
		if (args.length > 1)
			out = args[1];

		new TypCompiler().standAloneRun(in, out);
	}

	private void standAloneRun(String in, String out) {
		CharsetProbe probe = new CharsetProbe();
		String readCharset = probe.probeCharset(in);

		TypData data;
		try {
			data = compile(in, readCharset);
		} catch (SyntaxException e) {
			System.out.println(e.getMessage());
			return;
		} catch (FileNotFoundException e) {
			throw new MapFailedException("Could not open TYP file " + in + " to read");
		}

		try {
			writeTyp(data, out);
		} catch (IOException e) {
			System.out.println("Error writing file: " + e.getMessage());
		}
	}


	class CharsetProbe {
		private String codePage;
		private CharsetEncoder encoder;

		public CharsetProbe() {
			setCodePage("latin1");
		}

		private void setCodePage(String codePage) {
			this.codePage = codePage;
			this.encoder = Charset.forName(codePage).newEncoder();
		}

		private String probeCharset(String file) {
			String readingCharset = "utf-8";

			try {
				tryCharset(file, readingCharset);
				return readingCharset;
			} catch (TypLabelException e) {
				try {
					readingCharset = e.getCharsetName();
					tryCharset(file, readingCharset);
				} catch (Exception e1) {
					return null;
				}
			}

			return readingCharset;
		}

		private void tryCharset(String file, String readingCharset) {
			InputStream is = null;

			try {
				is = new FileInputStream(file);
				BufferedReader br = new BufferedReader(new InputStreamReader(is, readingCharset));

				String line;
				while ((line = br.readLine()) != null) {
					if (line.startsWith("CodePage")) {
						String[] split = line.split("=");
						try {
							setCodePage("cp" + (int) Integer.decode(split[1].trim()));
						} catch (NumberFormatException e) {
							setCodePage("cp1252");
						}
					}

					if (line.startsWith("String")) {
						CharBuffer cb = CharBuffer.wrap(line);
						if (encoder != null)
							encoder.encode(cb);
					}
				}
			} catch (UnsupportedEncodingException e) {
				throw new TypLabelException(codePage);

			} catch (CharacterCodingException e) {
				throw new TypLabelException(codePage);

			} catch (FileNotFoundException e) {
				throw new ExitException("File not found " + file);

			} catch (IOException e) {
				throw new ExitException("Could not read file " + file);

			} finally {
				Utils.closeFile(is);
			}
		}
	}
}
