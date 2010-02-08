/*
 * File: Version.java
 * 
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
 * Create date: 12 Dec 2007
 */

package uk.me.parabola.mkgmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Definitions of version numbers.
 *
 * @author Steve Ratcliffe
 */
public class Version {

	public static final String VERSION = getSvnVersion();

	// A default version to use.  This will be changed from time to time to
	// be the then current version number with a 'svn' suffix.  If this shows
	// up then a more accurate version was not available, but it may be useful
	// to know roughly.
	private static final String DEFAULT_VERSION = "svn";

	/**
	 * Get the version number if we can find one, else a default string.
	 * This looks in a file called version.properties on the classpath.
	 * This is created outside of the system by the build script.
	 *
	 * @return The version number or a default string if a version number
	 * cannot be found.
	 */
	private static String getSvnVersion() {
		InputStream is = Version.class.getResourceAsStream("/version.properties");

		if (is == null)
			return DEFAULT_VERSION;

		Properties props = new Properties();
		try {
			props.load(is);
		} catch (IOException e) {
			return DEFAULT_VERSION;
		}

		return props.getProperty("svn.version", DEFAULT_VERSION);
	}
}

