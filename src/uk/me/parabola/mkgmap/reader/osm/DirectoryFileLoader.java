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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.log.Logger;

/**
 * Deal with a style that is contained in a directory.
 *
 * @author Steve Ratcliffe
 */
public class DirectoryFileLoader extends StyleFileLoader {
	private static final Logger log = Logger.getLogger(DirectoryFileLoader.class);
	
	private File dir;

	/**
	 * Create a loader given the directory as a File object.
	 * @param dir The directory containing the style files.
	 */
	public DirectoryFileLoader(File dir) {
		assert dir.isDirectory();
		this.dir = dir;
	}

	/**
	 * Open the specified file in the style definition.
	 *
	 * @param file The name of the file in the style.
	 * @return An open file reader for the file.
	 */
	public Reader open(String file) throws FileNotFoundException {
		File f = new File(dir, file);
		Reader r = new FileReader(f);

		return new BufferedReader(r);
	}

	/**
	 * Nothing needs doing in this case.
	 */
	public void close() {
	}

	public String[] list() {
		log.debug("dir list", dir);
		List<String> res = new ArrayList<String>();

		File[] allFiles = dir.listFiles();
		for (File f : allFiles) {
			log.debug("dir loader", f);
			if (f.isDirectory()) {
				res.add(f.getName());
			}
		}

		// If there were no included directories, then the style name is the
		// name of the directory itself.
		if (res.isEmpty())
			res.add(dir.getName());

		return res.toArray(new String[res.size()]);
	}
}
