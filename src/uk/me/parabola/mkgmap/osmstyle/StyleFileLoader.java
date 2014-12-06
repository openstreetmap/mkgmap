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
package uk.me.parabola.mkgmap.osmstyle;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.net.URL;

import uk.me.parabola.log.Logger;

/**
 * Open a style which can be on the classpath (mainly for the built-in styles)
 * or in a specified file or directory.
 *
 * @author Steve Ratcliffe
 */
public abstract class StyleFileLoader implements Closeable {
	private static final Logger log = Logger.getLogger(StyleFileLoader.class);

	/**
	 * Open a style that is contained in a file.  This is expected to be a
	 * directory or zip file containing the files that make up the style.
	 *
	 * @param loc The file or directory containing the style(s). If this is null then the location "classpath:styles"
	 * is used.
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
			return createStyleLoader("classpath:styles", name);
		
		StyleFileLoader loader;

		File file = new File(loc);
		if (file.isDirectory()) {
			File dir = file;
			if (name != null) {
				dir = new File(file, name);
				if (dir.exists() == false)
					throw new FileNotFoundException("style " + name + " not found in " + dir);
				if (!dir.isDirectory())
					dir = file;
			}

			log.debug("style directory", dir);
			loader = new DirectoryFileLoader(dir);
		} else if (file.isFile()) {
			String loclc = loc.toLowerCase();
			if (loclc.endsWith(".style")) {
				if (name != null)
					throw new FileNotFoundException("no sub styles in a simple style file");
				log.debug("a single file style");
				loader = new CombinedStyleFileLoader(loc);
			} else {
				log.debug("jar file", file);
				loader = new JarFileLoader(file.toURI().toString(), name);
			}
		} else {
			log.debug("style url location", loc);
			String s = loc.toLowerCase();
			if (s.startsWith("classpath:")) {
				log.debug("load style off classpath");
				loader = classpathLoader(loc.substring(10), name);
				return loader;
			} else if (s.startsWith("jar:")) {
				loader = new JarFileLoader(loc, name);
			} else if (s.indexOf(':') > 0) {
				loader = new JarFileLoader(s, name);
			} else {
				throw new FileNotFoundException("no such file or path: " + loc);
			}
		}

		return loader;
	}

	/**
	 * Open the specified file in the style definition.
	 * @param filename The name of the file in the style.
	 * @return An open file reader for the file.
	 * @throws FileNotFoundException When the file can't be opened.
	 */
	public abstract Reader open(String filename) throws FileNotFoundException;

	/**
	 * Close the FileLoader.  This is different from closing individual files
	 * that were opened via {@link #open}.  After this call then you shouldn't
	 * open any more files.
	 */
	public abstract void close();

	/**
	 * List the names of the styles that are contained in this loader.
	 * @return An array of style names.
	 */
	public abstract String[] list();

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
			throw new FileNotFoundException("no classloader to find style");

		// all style files must be in the same directory or zip
		URL url = classLoader.getResource(path);
		if (url == null) {
			classLoader = StyleFileLoader.class.getClassLoader();
			url = classLoader.getResource(path);
			if (url == null)
				throw new FileNotFoundException("Could not find style " + path);
		}

		String proto = url.getProtocol().toLowerCase();
		if (proto.equals("jar")) {
			log.debug("classpath loading from jar with url", url);
			return new JarFileLoader(url);
		} else if (proto.equals("file")) {
			log.debug("classpath loading from directory", url.getPath());
			return new DirectoryFileLoader(new File(url.getPath()));
		}
		throw new FileNotFoundException("Could not load style from classpath");
	}
}
