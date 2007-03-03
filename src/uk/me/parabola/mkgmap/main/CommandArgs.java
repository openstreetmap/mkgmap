/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 01-Jan-2007
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.log.Logger;

import java.util.Properties;
import java.util.Enumeration;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Command line arguments for MakeMap.
 * Since it is likely that the number of options will become quick large, you
 * can place options in a file and have options given on the command line over-
 * ride them.
 *
 * @author Steve Ratcliffe
 */
class CommandArgs {
	private static final Logger log = Logger.getLogger(CommandArgs.class);

	private static final Properties defaults;
	static {
		defaults = new Properties();
		defaults.setProperty("mapname", "63240001");
		defaults.setProperty("description", "OSM street map");
	}

	// FIXME: the properties class is awful, will replace
	private Properties argvalues = new Properties(defaults);

	private String fileName;

	/**
	 * Read and interpret the command line arguments.  Most have a double hyphen
	 * preceeding them and these work just the same if they are in a config
	 * file.
	 *
	 * There are a few options that consist of a single hyphen followed by a
	 * single letter that are short cuts for a long option.
	 *
	 * The -c option is special.  It is followed by the name of a file in which
	 * there are further command line options.  Any option on the command line
	 * that comes after the -c option will override the value that is set in
	 * this file.
	 *
	 * @param args The command line arguments.
	 */
	public void readArgs(String[] args) {
		int i = 0;
		while (i < args.length) {
			String a = args[i++];
			if (a.startsWith("--")) {
				// This is a long style 'property' format option.
				setProperty(a.substring(2));
			} else if (a.equals("-c")) {
				// Config file
				readConfigFile(args[++i]);
			} else if (a.equals("-n")) {
				// Map name (should be an 8 digit number).
				argvalues.setProperty("mapname", args[++i]);
			} else if (a.startsWith("-")) {
				// this is an unrecognised option.
				log.warn("unrecognised option");
			} else {
				// A file name
				fileName = a;
			}
		}
	}

	/**
	 * Set a long property.  These have the form --name=value.  The '--' has
	 * already been stripped off when passed to this function.
	 *
	 * If there is no value part then the option will be set to the string "1".
	 *
	 * @param opt The option with leading '--' removed.  eg name=value.
	 */
	private void setProperty(String opt) {
		int eq = opt.indexOf('=');
		if (eq > 0) {
			String key = opt.substring(0, eq);
			String val = opt.substring(eq + 1);
			argvalues.setProperty(key, val);
		} else {
			argvalues.setProperty(opt, "1");
		}
	}

	private void readConfigFile(String filename) {
		Properties fileprops = new Properties(argvalues);
		try {
			InputStream is = new FileInputStream(filename);
			fileprops.load(is);
			argvalues = fileprops;
		} catch (FileNotFoundException e) {
			throw new ExitException("Cannot find configuration file " + filename, e);
		} catch (IOException e) {
			throw new ExitException("Error reading configuration file", e);
		}

	}

	public String getDescription() {
		return argvalues.getProperty("description");
	}

	public int getBlockSize() {
		return getValue("block-size", 512);
	}

	public String getFileName() {
		return fileName;
	}

	public String getMapname() {
		return argvalues.getProperty("mapname");
	}

	public String getCharset() {
		String s = argvalues.getProperty("latin1");
		if (s != null)
			return "latin1";

		return argvalues.getProperty("xcharset", "ascii");
	}

	public int getCodePage() {
		int cp;

		String s = argvalues.getProperty("xcode-page", "850");
		try {
			cp = Integer.parseInt(s);
		} catch (NumberFormatException e) {
			cp = 850;
		}
		
		return cp;
	}

	private int getValue(String name, int defval) {
		String s = argvalues.getProperty(name);
		if (s == null)
			return defval;

		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return defval;
		}
	}
}
