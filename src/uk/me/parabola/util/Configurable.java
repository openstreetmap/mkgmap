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
 * Create date: 03-Sep-2007
 */
package uk.me.parabola.util;

/**
 * A map reader that wants to inspect command line arguments.  A separate
 * interface as its only being used for the test maps at present.  Want
 * to leave open the possiblity of a more self describing interface that
 * might extend this one that would allow for a GUI interface.
 *
 * @author Steve Ratcliffe
 */
public interface Configurable {

	/**
	 * Used to mark that a reader needs to be configured by command line
	 * properties.  The MapReader will be given the command line properties
	 * that were set before it is asked to load the map.
	 *
	 * @param props The input properties.
	 */
	public void config(EnhancedProperties props);
}
