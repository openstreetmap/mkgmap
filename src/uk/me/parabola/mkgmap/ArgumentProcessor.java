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
	 * Process an option.  This is intended for options that change state or
	 * that say how the next filename is to be operated upon.
	 *
	 * @param opt The option name.
	 * @param val The option value.
	 */
	public void processOption(String opt, String val);

	/**
	 * Process a filename.
	 *
	 * @param args The option arguments.
	 * @param filename The filename.
	 */
	public void processFilename(CommandArgs args, String filename);

	/**
	 * Called when all the command line options have been processed.
	 * @param args
	 */
	public void endOptions(CommandArgs args);

	/**
	 * Called right at the beginning, before any command line options have
	 * been looked at.
	 */
	public void startOptions();
}
