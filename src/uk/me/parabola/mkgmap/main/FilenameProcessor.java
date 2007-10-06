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
package uk.me.parabola.mkgmap.main;

/**
 * Used when looping through filenames that are being processed into maps.
 * 
 * @author Steve Ratcliffe
 */
public interface FilenameProcessor {
	/**
	 * Process the given filename.
	 *
	 * @param args The user supplied arguments.
	 * @param filename The name of a file that was given to the program, eg
	 */
	void processFilename(CommandArgs args, String filename);

	/**
	 * Called when all the command line options have been processed.
	 */
	void endOfOptions();
}
