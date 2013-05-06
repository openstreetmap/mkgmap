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
 * The style can just be jared up at the top level or it can be
 * contained within a directory in the jar.  You can have more than one
 * style in the jar.  In this case a name will be required to select
 * the one that you want to use.  It looks for a file with a name that
 * ends with 'version' to work out where the style is.  If a name is given
 * then it looks for a file path ending {@code name/version}.
 *
 * @author Steve Ratcliffe
 */
public class JarFileLoader extends StyleFileLoader {
	private static final Logger log = Logger.getLogger(JarFileLoader.class);
	private JarFile jarFile;
	private String prefix;

	public JarFileLoader(URL url) throws FileNotFoundException {
		jarInit(url, null);
	}

	public JarFileLoader(String url, String name) throws FileNotFoundException {
		try {
			jarInit(new URL(makeJarUrl(url)), name);
		} catch (MalformedURLException e) {
			throw new FileNotFoundException("Could not open style at " + url);
		}
	}

	private String makeJarUrl(String url) {
		if (url.toLowerCase().startsWith("jar:"))
			return url;
		else
			return "jar:" + url + "!/";
	}

	private void jarInit(URL url, String name) throws FileNotFoundException {
		log.debug("opening", url);
		try {
			JarURLConnection jurl = (JarURLConnection) url.openConnection();
			jurl.setUseCaches(false);
			jarFile = jurl.getJarFile();
			prefix = jurl.getEntryName();
			if (prefix == null) {
				prefix = searchVersion(jarFile, name);
			}

			log.debug("jar prefix is", prefix);
		} catch (IOException e) {
			throw new FileNotFoundException("Could not open style at " + url);
		}
	}

	/**
	 * Find path in archive 
	 * @param file the JarFile instance
	 * @param style a style name or null to find any version file
	 * @return return prefix of (first) entry that contains file version
	 */
	private String searchVersion(JarFile file, String style) {
		Enumeration<JarEntry> en = file.entries();
		String flatEnd = style==null ? "version" : style + "/version";
		String end = "/" + flatEnd;
		while (en.hasMoreElements()) {
			JarEntry entry = en.nextElement();
			String ename = entry.getName();
			if (ename.endsWith(end) || ename.equals(flatEnd))
				return ename.substring(0, ename.length() - "version".length());
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

	protected void finalize() throws Throwable {
		super.finalize();
		close();
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

}
