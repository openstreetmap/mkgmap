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

import uk.me.parabola.log.Logger;

/**
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

	private void init(URL url) throws FileNotFoundException {
		JarURLConnection jurl;
		try {
			jurl = (JarURLConnection) url.openConnection();
			jarFile = jurl.getJarFile();
			prefix = jurl.getEntryName();
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open style at " + url);
		}
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

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}
}
