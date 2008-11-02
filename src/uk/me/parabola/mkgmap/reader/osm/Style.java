/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 02-Nov-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.util.Map;
import java.util.Properties;

/**
 * @author Steve Ratcliffe
 */
public interface Style {
	String[] getNameTagList();

	String getOption(String name);

	StyleInfo getInfo();

	Map<String, TypeRule> getWays();

	Map<String, TypeRule> getNodes();

	/**
	 * After the style is loaded we override any options that might
	 * have been set in the style itself with the command line options.
	 *
	 * We may have to filter some options that we don't ever want to
	 * set on the command line.
	 *
	 * @param config The command line options.
	 */
	void applyOptionOverride(Properties config);
}
