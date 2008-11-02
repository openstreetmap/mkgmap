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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import uk.me.parabola.log.Logger;

/**
 * Load a style from a jar file.
 *
 * The style can just be jar'ed up at the top level or it can be
 * contained within a directory in the jar.  You can have more than one
 * style in the jar.  In this case a name will be required to select
 * the one that you want to use.  It looks for a file with a name that
 * ends with 'version' to work out where the style is.  If a name is given
 * then it looks for a file path ending <code>name/version</code>.
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

	private JarFileLoader(File file) throws FileNotFoundException {
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

	public JarFileLoader(File file, String name) throws FileNotFoundException {
		this(file);
		if (name != null)
			setPrefix(searchPrefix(jarFile, '/' + name + "/version"));
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
		return searchPrefix(file, "/version");
	}

	private String searchPrefix(JarFile file, String end) {
		Enumeration<JarEntry> en = file.entries();
		while (en.hasMoreElements()) {
			JarEntry entry = en.nextElement();
			String name = entry.getName();
			if (name.endsWith(end))
				return name.substring(0, name.length() - 7);
		}
		return null;
	}

	/**
	 * Open the specified file in the style definition.
	 *
	 * @param filename The name of the file in the style.
	 * @return An open file reader for the file.
	 * @throws FileNotFoundException When the file can't be opened.
	 */
	public Reader open(String filename) throws FileNotFoundException {
		if (jarFile == null)
			throw new FileNotFoundException("Could not open file " + filename);

		String path = filename;
		if (prefix != null)
			path = prefix + filename;
		
		JarEntry jarEntry = jarFile.getJarEntry(path);
		if (jarEntry == null)
			throw new FileNotFoundException("Could not open style file " + filename);

		InputStream stream;
		try {
			stream = jarFile.getInputStream(jarEntry);
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open " + filename);
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

	public String[] list() {
		Enumeration<JarEntry> en = jarFile.entries();
		List<String> list = new ArrayList<String>();
		while (en.hasMoreElements()) {
			JarEntry entry = en.nextElement();

			if (!entry.isDirectory()) {
				String name = entry.getName();
				if (name.endsWith("version")) {
					log.debug("name is", name);
					String[] dirs = name.split("/");
					if (dirs.length == 1) {
						String s = jarFile.getName();
						s = s.replaceFirst("\\..*$", "");
						s = s.replaceAll(".*/", "");
						list.add(s);
					}
					else
						list.add(dirs[dirs.length - 2]);
				}
			}
		}

		return list.toArray(new String[list.size()]);
	}

	/**
	 * Set the prefix (ie the directory) the will be prepended to all file
	 * names that we try to open.
	 *
	 * @param prefix The prefix which should end in a slash.
	 */
	protected final void setPrefix(String prefix) {
		this.prefix = prefix;
	}
}
