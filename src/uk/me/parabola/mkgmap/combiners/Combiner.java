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
 * Create date: Dec 4, 2007
 */
package uk.me.parabola.mkgmap.combiners;

import uk.me.parabola.mkgmap.CommandArgs;

/**
 * The interface for all combining operations.  These include creating the
 * tdb file, the overview map and the gmapsupp.
 *
 * @author Steve Ratcliffe
 */
public interface Combiner {

	/**
	 * Initialise with the command line arguments.  This is called after all
	 * the command line arguments have been processed, but before any calls to
	 * the {@link #onMapEnd} methods.
	 *
	 * @param args The command line arguments.
	 */
	public void init(CommandArgs args);

	/**
	 * This is called when an individual map is complete.
	 *
	 * @param info An interface to read the map.
	 */
	public void onMapEnd(FileInfo info);

	/**
	 * The complete map set has been processed.  Finish off anything that needs
	 * doing.
	 */
	public void onFinish();

	public default String getFilename() {
		return null;
	}
}
