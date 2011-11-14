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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.channels.FileChannel;

import uk.me.parabola.imgfmt.app.typ.TYPFile;
import uk.me.parabola.imgfmt.app.typ.TypLabelException;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
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
public class TypCompiler {
	public static void main(String[] args) throws IOException {
		String in = "default.txt";
		if (args.length > 0)
			in = args[0];
		String out = "OUT.TYP";
		if (args.length > 1)
			out = args[1];

		try {
			compile(in, out, "utf-8");
		} catch (TypLabelException e) {
			compile(in, out, e.getCharsetName());
		}
	}

	private static void compile(String in, String out, String charset) throws IOException {
		InputStream is = new FileInputStream(in);
		Reader fileReader = new InputStreamReader(is, charset);
		//String encoding = fileReader.getEncoding();
		//System.out.println("enc " + encoding);
		Reader r = new BufferedReader(fileReader);

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
		typ.setData(tr.getData());

		typ.write();
		typ.close();
	}
}
