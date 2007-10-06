/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 24-Sep-2007
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;

/**
 * The new main program.  There can be many filenames to process and there can
 * be differing outputs determined by options.  So the actual work is mostly
 * done in other classes.  This one just works out what is wanted.
 *
 * @author Steve Ratcliffe
 */
public class Main implements ArgumentProcessor {
	private static final Logger log = Logger.getLogger(Main.class);

	//private OverviewMapMaker overview;
	private final MapProcessor action;

	public Main() {
		//overview = new OverviewMapMaker();

		// The default is to make a map
		action = new MakeMap();
	}

	public static void main(String[] args) {

		// We need at least one argument.
		if (args.length < 1) {
			System.err.println("Usage: mkgmap <file.osm>");
			System.exit(1);
		}

		Main mm = new Main();

		try {
			// Read the command line arguments and process each filename found.
			CommandArgs commandArgs = new CommandArgs(mm);
			commandArgs.readArgs(args);
		} catch (ExitException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	public void processOption(String opt, String val) {
		log.debug("option:", opt, val);
		
		if (opt.equals("number-of-files")) {

			// This option always appears first.  We use it to turn off
			// generation of the overview files if there is only one file
			// to process.
			int n = Integer.valueOf(val);
			if (n == 1) {
				action.optionOff(MapOption.OVERVIEW_MAP);
			}
		}
	}

	/**
	 * Switch out to the appropriate class to process the filename.
	 *
	 * @param args The command arguments.
	 * @param filename The filename to process.
	 */
	public void processFilename(CommandArgs args, String filename) {
		action.processFilename(args, filename);
	}

	public void endOfOptions() {
		action.endOfOptions();
	}
}
