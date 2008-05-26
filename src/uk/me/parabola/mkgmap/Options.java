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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.log.Logger;

/**
 * Holds and reads options.  Like a properties file, but order is important
 * and events are generated when options are read.
 *
 * This should be used by CommandArgs and will do eventually.
 *
 * @author Steve Ratcliffe
 */
public class Options {
	private static final Logger log = Logger.getLogger(Options.class);

	private final OptionProcessor proc;

	// Used to prevent the same file being read more than once.
	private final Set<String> readFiles = new HashSet<String>();

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

		try {
			Reader r = new FileReader(filename);
			readOptionFile(r);
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not read option file " + filename, e);
		} catch (IOException e) {
			throw new ExitException("Reading option file " + filename + " failed", e);
		}
	}

	public void readOptionFile(Reader r) throws IOException {
		BufferedReader br = new BufferedReader(r);

		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0 || line.charAt(0) == '#')
				continue;

			Option opt = new Option(line);
			proc.processOption(opt);
		}
	}
}
