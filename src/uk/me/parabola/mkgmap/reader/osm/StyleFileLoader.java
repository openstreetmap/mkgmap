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

import uk.me.parabola.log.Logger;

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
	private static final Logger log = Logger.getLogger(StyleFileLoader.class);

	/**
	 * Open a style that is contained in a file.  This is expected to be a
	 * directory or zip file containing the files that make up the style.
	 *
	 * @param loc The file or directory containing the style(s).
	 * @param name If the name is given then we look for a directory with the
	 * given name.  If there is no name, then the style is assumed to be at
	 * the top level and/or the only file.
	 *
	 * @return A style loader.
	 */
	public static StyleFileLoader createStyleLoader(String loc, String name)
			throws FileNotFoundException
	{
		if (loc == null)
			return createStyleLoaderByName(name);
		
		StyleFileLoader loader;

		File f = new File(loc);
		if (f.isDirectory()) {
			File dir = f;
			if (name != null)
				dir = new File(f, name);

			log.debug("style directory", dir);
			loader = new DirectoryFileLoader(dir);
		} else if (f.isFile()) {
			log.debug("jar file", f);
			loader = new JarFileLoader(f, name);
		} else {
			log.debug("style url location", loc);
			String s = loc.toLowerCase();
			if (s.startsWith("classpath:")) {
				log.debug("load style off classpath");
				loader = classpathLoader(s.substring(10), name);
				return loader;
			} else if (s.startsWith("jar:")) {
				loader = new JarFileLoader(loc, name);
			} else if (s.indexOf(':') > 0) {
				loader = new JarFileLoader("jar:" + s + "!/");
			} else {
				loader = classpathLoader("styles/", name);
			}
		}

		return loader;
	}

	/**
	 * Load a style by name only.  This implies that it will loaded from the
	 * classpath.
	 *
	 * @param name The style name.  It will be a built in one, or otherwise
	 * on the classpath.
	 *
	 * @return The loader.
	 */
	public static StyleFileLoader createStyleLoaderByName(String name)
			throws FileNotFoundException
	{
		return createStyleLoader("classpath:styles", name);
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
	 * that were opened via {@link #open}.  After this call then you shouldn't
	 * open any more files.
	 */
	public abstract void close();

	/**
	 * Find a style on the class path.  First we find out if the style is in
	 * a jar or a directory and then use the appropriate Loader.
	 *
	 * @param loc The file or directory location.
	 * @param name The style name.
	 * @return A loader for the style.
	 * @throws FileNotFoundException If it can't be found.
	 */
	private static StyleFileLoader classpathLoader(String loc, String name) throws FileNotFoundException
	{
		String path = loc;
		if (name != null)
			path = loc + '/' + name + '/';

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		if (classLoader == null)
			throw new FileNotFoundException("cannot file style");

		// all style files must be in the same directory or zip
		URL url = classLoader.getResource(path);
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
