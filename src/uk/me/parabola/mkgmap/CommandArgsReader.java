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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Command line arguments for Main.  Arguments consist of options and filenames.
 * You read arguments from left to right and when a filename is encountered
 * the file is processed with the options that were in force at the time.
 * 
 * Since it is likely that the number of options will become quite large, you
 * can place options in a file.  Place the options each on a separate line
 * without the initial '--'.
 *
 * @author Steve Ratcliffe
 */
public class CommandArgsReader {
	private static final Logger log = Logger.getLogger(CommandArgsReader.class);

	private final ArgumentProcessor proc;

	private boolean mapnameWasSet;

	private final ArgList arglist = new ArgList();

	private final EnhancedProperties args = new EnhancedProperties();
	private Set<String> validOptions;

	{
		// Set some default values.  It is as if these were on the command
		// line before any user supplied options.
		add(new CommandOption("mapname", "63240001"));
		add(new CommandOption("description", "OSM street map"));
		add(new CommandOption("overview-mapname", "osmmap"));
		add(new CommandOption("overview-mapnumber", "63240000"));
		add(new CommandOption("poi-address", ""));
	}

	public CommandArgsReader(ArgumentProcessor proc) {
		this.proc = proc;
	}

	/**
	 * Read and interpret the command line arguments.  Most have a double hyphen
	 * preceding them and these work just the same if they are in a config
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
				add(new Filename(arg));
			}
		}

		// If there is more than one filename argument we inform of this fact
		// via a fake option.
		proc.processOption("number-of-files", String.valueOf(arglist.getFilenameCount()));

		// Now process the arguments in order.
		for (ArgType a : arglist) {
			a.processArg();
		}

		proc.endOptions(new CommandArgs(this.args));
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

		if (validOptions != null && !validOptions.contains(option) && !opt.isExperimental()) {
			Formatter f = new Formatter();
			f.format("Invalid option: '%s'", option);
			throw new ExitException(f.toString());
		}

		log.debug("adding option", option, value);

		// Note if an explicit mapname is set
		if (option.equals("mapname"))
			mapnameWasSet = true;

		if (option.equals("input-file")) {
			log.debug("adding filename", value);
			add(new Filename(value));
		} else if (option.equals("read-config")) {
			readConfigFile(value);
		} else if (option.equals("latin1")) {
			add(new CommandOption("code-page", "1252"));
		} else {
			add(opt);
		}
	}

	private void add(CommandOption option) {
		arglist.add(option);
	}

	private void add(Filename filename) {
		arglist.add(filename);
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

	public void setValidOptions(Set<String> validOptions) {
		this.validOptions = validOptions;
	}

	/**
	 * Interface that represents an argument type.  It provides a method for
	 * the argument to be processed in order.  Options can be interspersed with
	 * filenames.  The options take effect where they appear.
	 */
	interface ArgType {
		public abstract void processArg();
	}

	/**
	 * A filename.
	 */
	class Filename implements ArgType {
		private final String name;
		private boolean useFilenameAsMapname = true;

		private Filename(String name) {
			this.name = name;
			if (mapnameWasSet)
				useFilenameAsMapname = false;
		}

		public void processArg() {
			// If there was no explicit mapname specified and the input filename
			// looks like it contains an 8digit number then we use that.
			String mapname;
			if (useFilenameAsMapname) {
				mapname = extractMapName(name);
				if (mapname != null)
					args.setProperty("mapname", mapname);
			}

			// Now process the file
			proc.processFilename(new CommandArgs(args), name);

			// Increase the name number.  If the next arg sets it then that
			// will override this new name.
			mapname = args.getProperty("mapname");
			try {
				Formatter fmt = new Formatter();
				try {
					int n = Integer.parseInt(mapname);
					fmt.format("%08d", ++n);
				} catch (NumberFormatException e) {
					fmt.format("%8.8s", mapname);
				}
				args.setProperty("mapname", fmt.toString());
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
	class CommandOption implements ArgType {
		private final Option option;

		private CommandOption(Option option) {
			this.option = option;
		}

		private CommandOption(String key, String val) {
			this.option = new Option(key, val);
		}

		public void processArg() {
			if (option.isReset()) {
				args.remove(option.getOption());
				proc.removeOption(option.getOption());
			} else {
				args.setProperty(option.getOption(), option.getValue());
				proc.processOption(option.getOption(), option.getValue());
			}
		}

		public String getOption() {
			return option.getOption();
		}

		public String getValue() {
			return option.getValue();
		}

		public boolean isExperimental() {
			return option.isExperimental();
		}
	}

	/**
	 * The arguments are held in this list.
	 */
	class ArgList implements Iterable<ArgType> {
		private final List<ArgType> alist;

		private int filenameCount;

		ArgList() {
			alist = new ArrayList<ArgType>();
		}

		protected void add(CommandOption option) {
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
	}
}
