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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Enumeration;

/**
 * Load a style from a jar file.
 * 
 * @author Steve Ratcliffe
 */
public class JarFileLoader extends StyleFileLoader {
	private static final Logger log = Logger.getLogger(JarFileLoader.class);
	private JarFile jarFile;
	private String prefix;

	public JarFileLoader(URL url) throws FileNotFoundException {
		init(url);
	}

	public JarFileLoader(File file) throws FileNotFoundException {
		try {
			jarFile = new JarFile(file);
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open style " + file);
		}
	}

	public JarFileLoader(String url) throws FileNotFoundException {
		try {
			init(new URL(url));
		} catch (MalformedURLException e) {
			throw new FileNotFoundException("Could not open style at " + url);
		}
	}

	public JarFileLoader(String url, String name) throws FileNotFoundException {
		this(url);
		if (name != null)
			setPrefix(name + '/');
	}

	public JarFileLoader(File f, String name) throws FileNotFoundException {
		this(f);
		if (name != null)
			setPrefix(name + '/');
	}

	private void init(URL url) throws FileNotFoundException {
		try {
			JarURLConnection jurl = (JarURLConnection) url.openConnection();
			jarFile = jurl.getJarFile();
			prefix = jurl.getEntryName();
			if (prefix == null)
				prefix = searchPrefix(jarFile);
			log.debug("jar prefix is", prefix);
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open style at " + url);
		}
	}

	private String searchPrefix(JarFile file) {
		Enumeration<JarEntry> en = file.entries();
		while (en.hasMoreElements()) {
			JarEntry entry = en.nextElement();
			String name = entry.getName();
			if (name.endsWith("/version"))
				return name.substring(0, name.length() - 7);
		}
		return null;
	}

	/**
	 * Open the specified file in the style definition.
	 *
	 * @param file The name of the file in the style.
	 * @return An open file reader for the file.
	 * @throws FileNotFoundException When the file can't be opened.
	 */
	public Reader open(String file) throws FileNotFoundException {
		if (jarFile == null)
			throw new FileNotFoundException("Could not open file " + file);

		String path = file;
		if (prefix != null)
			path = prefix + file;
		
		JarEntry jarEntry = jarFile.getJarEntry(path);
		if (jarEntry == null)
			throw new FileNotFoundException("Could not open style file " + file);

		InputStream stream;
		try {
			stream = jarFile.getInputStream(jarEntry);
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open " + file);
		}
		return new InputStreamReader(new BufferedInputStream(stream));
	}

	public void close() {
		try {
			jarFile.close();
		} catch (IOException e) {
			log.debug("failed to close jar file");
		}
	}

	/**
	 * Set the prefix (ie the directory) the will be prepended to all file
	 * names that we try to open.
	 *
	 * @param prefix The prefix which should end in a slash.
	 */
	public final void setPrefix(String prefix) {
		this.prefix = prefix;
	}
}
