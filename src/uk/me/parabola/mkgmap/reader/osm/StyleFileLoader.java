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
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.net.URL;

/**
 * Open a style which can be on the classpath (mainly for the built-in styles)
 * or in a specified file or directory.
 *
 * @author Steve Ratcliffe
 */
public abstract class StyleFileLoader {

	/**
	 * Open a style that is contained in a file.  This is expected to be a
	 * directory or zip file containing the files that make up the style.
	 * @param file The file to open.
	 * @return A style loader.
	 */

	public static StyleFileLoader createStyleLoader(String file, String name) {
		return null;
	}
	public static StyleFileLoader createStyleLoader(String file) {
		return null;
	}
	public static StyleFileLoader createStyleLoaderByName(String name) {
		return null;
	}
	public static StyleFileLoader xcreateStyleLoader(String loc) throws FileNotFoundException
	{
		File f = new File(loc);
		if (f.isDirectory()) {
			return new DirectoryFileLoader(f);
		} else if (f.isFile()) {
			return new JarFileLoader(f);
		} else {
			String s = loc.toLowerCase();
			if (s.startsWith("classpath:")) {
				return classpathLoader(s);
			} else if (s.startsWith("jar:")) {
				return new JarFileLoader(s);
			} else if (s.indexOf(':') > 0) {
				return new JarFileLoader("jar:" + s + "!/");
			}
		}

		return classpathLoader("styles/" + loc + "/");
	}

	/**
	 * Open the specified file in the style definition.
	 * @param file The name of the file in the style.
	 * @return An open file reader for the file.
	 * @throws FileNotFoundException When the file can't be opened.
	 */
	public abstract Reader open(String file) throws FileNotFoundException;

	/**
	 * Close the FileLoader.  This is different from closing individual files
	 * that opened via {@link #open}.  After this call then you shouldn't open
	 * any more files.
	 */
	public abstract void close();

	private static StyleFileLoader classpathLoader(String path) throws FileNotFoundException
	{
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null)
			throw new FileNotFoundException("cannot file style");

		// all style files must be in the same directory or zip
		URL url = loader.getResource(path);
		if (url == null)
			throw new FileNotFoundException("Could not file style " + path);

		String proto = url.getProtocol().toLowerCase();
		if (proto.equals("jar")) {
			return new JarFileLoader(url);
		} else if (proto.equals("file")) {
			return new DirectoryFileLoader(new File(url.getPath()));
		}
		throw new FileNotFoundException("Could not load style from classpath");
	}
}
