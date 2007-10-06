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
import uk.me.parabola.mkgmap.ExitException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Command line arguments for Main.
 * 
 * Since it is likely that the number of options will become quite large, you
 * can place options in a file and have options given on the command line over-
 * ride them.
 *
 * @author Steve Ratcliffe
 */
class CommandArgs {
	private static final Logger log = Logger.getLogger(CommandArgs.class);

	private final ArgList arglist = new ArgList();
	{
		// Set some default values.  It is as if these were on the command
		// line before any user supplied options.
		arglist.add(new Option("mapname", "63240001"));
		arglist.add(new Option("description", "OSM street map"));
		arglist.add(new Option("overview-name", "63240000"));
	}

	private final ArgumentProcessor proc;
	private final Properties currentOptions = new Properties();

	CommandArgs(ArgumentProcessor proc) {
		this.proc = proc;
	}

	/**
	 * Read and interpret the command line arguments.  Most have a double hyphen
	 * preceeding them and these work just the same if they are in a config
	 * file.
	 * <p/>
	 * There are a few options that consist of a single hyphen followed by a
	 * single letter that are short cuts for a long option.
	 * <p/>
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
			String arg = args[i++];
			if (arg.startsWith("--")) {
				// This is a long style 'property' format option.
				arglist.add(new Option(arg.substring(2)));

			} else if (arg.equals("-c")) {
				// Config file
				readConfigFile(args[i++]);

			} else if (arg.equals("-n")) {
				// Map name (should be an 8 digit number).
				arglist.add(new Option("mapname", args[i++]));

			} else if (arg.startsWith("-")) {
				// this is an unrecognised option.
				System.err.println("unrecognised option " + arg);

			} else {
				// A file name
				arglist.add(new Filename(arg));
			}
		}

		// If there is more than one filename argument we inform of this fact
		// via a fake option.
		proc.processOption("number-of-files", String.valueOf(arglist.getFilenameCount()));
		
		// Now process the arguments in order.
		for (ArgType a : arglist) {
			a.processArg();
		}

		proc.endOfOptions();
	}

	public Properties getProperties() {
		return arglist.getProperties();
	}

	public String getDescription() {
		return arglist.getProperty("description");
	}

	public int getBlockSize() {
		return getValue("block-size", 512);
	}

	public String getMapname() {
		return arglist.getProperty("mapname");
	}

	public String getCharset() {
		String s = arglist.getProperty("latin1");
		if (s != null)
			return "latin1";

		// xcharset is the old value, use charset instead.
		return arglist.getProperty("charset", arglist.getProperty("xcharset", "ascii"));
	}

	public int getCodePage() {
		int cp;

		String s = arglist.getProperty("xcode-page", "850");
		try {
			cp = Integer.parseInt(s);
		} catch (NumberFormatException e) {
			cp = 850;
		}

		return cp;
	}

	/**
	 * Get an integer value.  A default is used if the property does not exist.
	 *
	 * @param name   The name of the property.
	 * @param defval The default value to supply.
	 * @return An integer that is the value of the property.  If the property
	 *         does not exist or if it is not numeric then the default value is returned.
	 */
	private int getValue(String name, int defval) {
		String s = arglist.getProperty(name);
		if (s == null)
			return defval;

		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return defval;
		}
	}

	/**
	 * Set a long property.  These have the form --name=value.  The '--' has
	 * already been stripped off when passed to this function.
	 * <p/>
	 * If there is no value part then the option will be set to the string "1".
	 *
	 * @param opt The option with leading '--' removed.  eg name=value.
	 */
	private void setPropertyFromArg(String opt) {
		int eq = opt.indexOf('=');
		if (eq > 0) {
			String key = opt.substring(0, eq).trim();
			String val = opt.substring(eq + 1).trim();
			arglist.add(new Option((key), val));
		} else {
			arglist.add(new Option((opt), "1"));
		}
	}

	/**
	 * Read a config file that contains more options.  When the number of
	 * options becomes large it is more convenient to place them in a file.
	 *
	 * @param filename The filename to obtain options from.
	 */
	private void readConfigFile(String filename) {
		log.info("reading config file", filename);
		try {
			Reader r = new FileReader(filename);
			BufferedReader br = new BufferedReader(r);

			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0 || line.charAt(0) == '#')
					continue;

				// we don't allow continuation lines yet...
				setPropertyFromArg(line);
			}
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not read option file " + filename);
		} catch (IOException e) {
			throw new ExitException("Reading option file " + filename + " failed");
		}
	}

	/**
	 * The arguments are held in this list.
	 */
	private class ArgList implements Iterable<ArgType> {
		private final List<ArgType> arglist = new ArrayList<ArgType>();

		private int filenameCount;

		public void add(Option option) {
			arglist.add(option);
		}

		public void add(Filename name) {
			filenameCount++;
			arglist.add(name);
		}

		public Iterator<ArgType> iterator() {
			return arglist.iterator();
		}

		public int getFilenameCount() {
			return filenameCount;
		}

		public String getProperty(String name) {
			return currentOptions.getProperty(name);
		}

		public String getProperty(String name, String def) {
			String val = currentOptions.getProperty(name);
			if (val == null)
				val = def;
			return val;
		}

		public Properties getProperties() {
			return currentOptions;
		}

		public void setProperty(String name, String value) {
			currentOptions.put(name, value);
		}
	}

	/**
	 * Interface that represents an argument type.  It provides a method for
	 * the argument to be processed in order.  Options can be intersperced with
	 * filenames.  The options take effect where they appear.
	 */
	private interface ArgType {
		public abstract void processArg();
	}

	/**
	 * A filename.
	 */
	private class Filename implements ArgType {
		private final String name;

		private Filename(String name) {
			this.name = name;
		}

		public void processArg() {
			proc.processFilename(CommandArgs.this, name);
			String mapname = arglist.getProperty("mapname");

			try {
				// Increase the name number.  If the next arg sets it then that
				// will override this new name.
				int n = Integer.parseInt(mapname);
				Formatter fmt = new Formatter();
				fmt.format("%08d", ++n);
				arglist.setProperty("mapname", fmt.toString());
			} catch (NumberFormatException e) {
				// If the name is not a number then we just leave it alone...
			}
		}
	}

	/**
	 * An option argument.  A key value pair.
	 */
	private class Option implements ArgType {
		private final String opt;
		private final String value;

		private Option(String optval) {
			int eq = optval.indexOf('=');
			if (eq > 0) {
				opt = optval.substring(0, eq);
				value = optval.substring(eq + 1);
			} else {
				opt = optval;
				value = "1";
			}
		}

		private Option(String opt, String value) {
			this.opt = opt;
			this.value = value;
		}

		public void processArg() {
			currentOptions.setProperty(opt, value);
			proc.processOption(opt, value);
		}

		public String getOpt() {
			return opt;
		}

		public String getValue() {
			return value;
		}

	}

}
