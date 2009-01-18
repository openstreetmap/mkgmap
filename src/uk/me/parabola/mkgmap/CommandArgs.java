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
package uk.me.parabola.mkgmap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Command line arguments for Main.  Arguments consist of options and filenames.
 * You read arguments from left to right and when a filename is encounted
 * the file is processed with the options that were in force at the time.
 * 
 * Since it is likely that the number of options will become quite large, you
 * can place options in a file.  Place the options each on a separate line
 * without the initial '--'.
 *
 * @author Steve Ratcliffe
 */
public class CommandArgs {
	private static final Logger log = Logger.getLogger(CommandArgs.class);

	private final ArgList arglist = new ArgList();

	{
		// Set some default values.  It is as if these were on the command
		// line before any user supplied options.
		arglist.add(new CommandOption("mapname", "63240001"));
		arglist.add(new CommandOption("description", "OSM street map"));
		arglist.add(new CommandOption("overview-name", "63240000"));
	}

	private final ArgumentProcessor proc;
	private final EnhancedProperties currentOptions = new EnhancedProperties();

	private boolean mapnameWasSet;

	public CommandArgs(ArgumentProcessor proc) {
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

		proc.startOptions();

		int i = 0;
		while (i < args.length) {
			String arg = args[i++];
			if (arg.startsWith("--")) {
				// This is a long style 'property' format option.
				addOption(arg.substring(2));

			} else if (arg.equals("-c")) {
				// Config file
				readConfigFile(args[i++]);

			} else if (arg.equals("-n")) {
				// Map name (should be an 8 digit number).
				addOption("mapname", args[i++]);

			} else if (arg.equals("-v")) {
				// make commands more verbose
				addOption("verbose");

			} else if (arg.startsWith("-")) {
				// this is an unrecognised option.
				System.err.println("unrecognised option " + arg);

			} else {
				log.debug("adding filename:", arg);
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

		proc.endOptions(this);
	}

	public EnhancedProperties getProperties() {
		return arglist.getProperties();
	}

	public int get(String name, int def) {
		return currentOptions.getProperty(name, def);
	}

	public String get(String name, String def) {
		return currentOptions.getProperty(name, def);
	}

	// ////
	// There are a number of methods to get specific arguments that follow.
	// There are many more options in use however.  New code should mostly
	// just use the get methods above.
	// ////

	public String getDescription() {
		return arglist.getProperty("description");
	}

	public int getBlockSize() {
		return get("block-size", 512);
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

		// xcode-page is the old name
		String s = arglist.getProperty("code-page", arglist.getProperty("xcode-page", "0"));
		try {
			cp = Integer.parseInt(s);
		} catch (NumberFormatException e) {
			cp = 0;
		}

		return cp;
	}

	public boolean isForceUpper() {
		return arglist.getProperty("lower-case") == null;
	}

	/**
	 * Test for the existence of an argument.
	 */
	public boolean exists(String name) {
		return currentOptions.containsKey(name);
	}

	/**
	 * Add an option based on the option and value separately.
	 * @param option The option name.
	 * @param value Its value.
	 */
	private void addOption(String option, String value) {
		CommandOption opt = new CommandOption(option, value);
		addOption(opt);
	}

	/**
	 * Add an option from a raw string.
	 * @param optval The option=value string.
	 */
	private void addOption(String optval) {
		CommandOption opt = new CommandOption(new Option(optval));
		addOption(opt);
	}

	/**
	 * Actually add the option.  Some of these are special in that they are
	 * filename arguments or instructions to read options from another file.
	 *
	 * @param opt The decoded option.
	 */
	private void addOption(CommandOption opt) {
		String option = opt.getOption();
		String value = opt.getValue();

		log.debug("adding option", option, value);

		// Note if an explicit mapname is set
		if (option.equals("mapname"))
			mapnameWasSet = true;

		if (option.equals("input-file")) {
			log.debug("adding filename", value);
			arglist.add(new Filename(value));
		} else if (option.equals("read-config")) {
			readConfigFile(value);
		} else {
			arglist.add(opt);
		}
	}

	/**
	 * Read a config file that contains more options.  When the number of
	 * options becomes large it is more convenient to place them in a file.
	 *
	 * @param filename The filename to obtain options from.
	 */
	private void readConfigFile(String filename) {
		Options opts = new Options(new OptionProcessor() {
			public void processOption(Option opt) {
				log.debug("incoming opt", opt.getOption(), opt.getValue());
				addOption(new CommandOption(opt));
			}
		});
		try {
			opts.readOptionFile(filename);
		} catch (IOException e) {
			throw new ExitException("Failed to read option file", e);
		}
	}

	/**
	 * The arguments are held in this list.
	 */
	private class ArgList implements Iterable<ArgType> {
		private final List<ArgType> alist = new ArrayList<ArgType>();

		private int filenameCount;

		public void add(CommandOption option) {
			alist.add(option);
		}

		public void add(Filename name) {
			filenameCount++;
			alist.add(name);
		}

		public Iterator<ArgType> iterator() {
			return alist.iterator();
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

		public EnhancedProperties getProperties() {
			return currentOptions;
		}

		public void setProperty(String name, String value) {
			currentOptions.setProperty(name, value);
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
			// If there was no explicit mapname specified and the input filename
			// looks like it contains an 8digit number then we use that.
			String mapname;
			if (!mapnameWasSet) {
				mapname = extractMapName(name);
				if (mapname != null)
					arglist.setProperty("mapname", mapname);
			}

			// Now process the file
			proc.processFilename(CommandArgs.this, name);

			// Increase the name number.  If the next arg sets it then that
			// will override this new name.
			mapnameWasSet = false;
			mapname = arglist.getProperty("mapname");
			try {
				Formatter fmt = new Formatter();
				try {
					int n = Integer.parseInt(mapname);
					fmt.format("%08d", ++n);
				} catch (NumberFormatException e) {
					fmt.format("%8.8s", mapname);
				}
				arglist.setProperty("mapname", fmt.toString());
			} catch (NumberFormatException e) {
				// If the name is not a number then we just leave it alone...
			}
		}

		private String extractMapName(String path) {

			File file = new File(path);
			String fname = file.getName();
			Pattern pat = Pattern.compile("([0-9]{8})");
			Matcher matcher = pat.matcher(fname);
			boolean found = matcher.find();
			if (found)
				return matcher.group(1);

			return null;
		}
	}

	/**
	 * An option argument.  A key value pair.
	 */
	private class CommandOption implements ArgType {
		private final Option option;

		private CommandOption(Option option) {
			this.option = option;
		}

		private CommandOption(String key, String val) {
			this.option = new Option(key, val);
		}

		public void processArg() {
			currentOptions.setProperty(option.getOption(), option.getValue());
			proc.processOption(option.getOption(), option.getValue());
		}

		public String getOption() {
			return option.getOption();
		}

		public String getValue() {
			return option.getValue();
		}
	}

}
