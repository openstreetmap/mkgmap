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

import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Different options that can be supplied that change the mode and operation
 * of the program.
 * 
 * @author Steve Ratcliffe
 */
public interface MapProcessor {

	/**
	 * Make a map from the given input filename and options.
	 *
	 * @param args The options that are in force.
	 * @param filename The input filename.
	 * @return The output filename; the name of the file that was created.
	 */
	public String makeMap(CommandArgs args, String filename);
}