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
 * Create date: 29-Sep-2007
 */
package uk.me.parabola.mkgmap;

/**
 * Used to step through each filename that is given to the program.
 *
 * @author Steve Ratcliffe
 */
public interface ArgumentProcessor  {

	/**
	 * Process an option. In general you do not do anything in this callback for most options.
	 * Options that determine how a particular file are processed are saved into a map that
	 * is handed to the map building process.
	 *
	 * Options that are processed here are things like --help or --list-styles that have an
	 * actual effect by themselves.
	 *
	 * @param opt The option name.
	 * @param val The option value.
	 */
	public void processOption(String opt, String val);

	/**
	 * Called when an option is reset, eg --no-tdbfile.
	 * @param opt The option name.
	 */
	public void removeOption(String opt);

	/**
	 * Process a filename.
	 *
	 * @param args A copy of the option arguments in force when this filename
	 * was specified.
	 * @param filename The filename.
	 */
	public void processFilename(CommandArgs args, String filename);

	/**
	 * Called when all the command line options have been processed.
	 * @param args The command line options.
	 */
	public void endOptions(CommandArgs args);

	/**
	 * Called right at the beginning, before any command line options have
	 * been looked at.
	 */
	public void startOptions();
}
