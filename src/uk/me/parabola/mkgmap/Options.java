/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: May 26, 2008
 */
package uk.me.parabola.mkgmap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Holds and reads options.  Like a properties file, but order is important
 * and events are generated when options are read.
 *
 * You can use the normal option syntax <tt>foo=bar</tt>.
 * You can also use <tt>foo: bar</tt> and for longer options that
 * span several lines <tt>foo { this can span lines }</tt>
 *
 * @author Steve Ratcliffe
 */
public class Options {
	private static final Logger log = Logger.getLogger(Options.class);

	private final OptionProcessor proc;

	// Used to prevent the same file being read more than once.
	private final Collection<String> readFiles = new HashSet<String>();

	public Options(OptionProcessor proc) {
		this.proc = proc;
	}

	/**
	 * Read a config file that contains more options.  When the number of
	 * options becomes large it is more convenient to place them in a file.
	 *
	 * If the same file is read more than once, then the second time
	 * will be ignored.
	 *
	 * @param filename The filename to obtain options from.
	 */
	public void readOptionFile(String filename) throws IOException {
		log.info("reading option file", filename);

		File file = new File(filename);
		try {
			// Don't read the same file twice.
			String path = file.getCanonicalPath();
			if (readFiles.contains(path))
				return;
			readFiles.add(path);
		} catch (IOException e) {
			// Probably want to do more than warn here.
			log.warn("the config file could not be read");
			return;
		}

		Reader r = new FileReader(filename);
		readOptionFile(r, filename);
	}

	public void readOptionFile(Reader r, String filename) {
		BufferedReader br = new BufferedReader(r);
		TokenScanner ts = new TokenScanner(filename, br);
		ts.setExtraWordChars("-");

		while (!ts.isEndOfFile()) {
			Token tok = ts.nextToken();
			if (tok.isValue("#")) {
				ts.skipLine();
				continue;
			}

			String key = tok.getValue();

			ts.skipSpace();
			tok = ts.peekToken();
			
			if (tok.getType() == TokType.SYMBOL) {

				String punc = ts.nextValue();
				String val;
				if (punc.equals(":") || punc.equals("=")) {
					val = ts.readLine();
				} else if (punc.equals("{")) {
					ts.skipSpace();
					val = ts.readUntil(TokType.SYMBOL, "}");
					ts.nextToken();  // discard the closing brace
				} else {
					ts.skipLine();
					continue;
				}
				proc.processOption(new Option(key, val));
			} else if (key != null){
				proc.processOption(new Option(key, ""));
			} else {
				ts.skipLine();
			}
		}
	}
}
