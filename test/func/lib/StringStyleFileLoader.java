/*
 * Copyright (C) 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package func.lib;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.mkgmap.osmstyle.StyleFileLoader;

/**
 * A style file loader where all the files are held as strings within the loader itself.
 *
 * A file name and contents can be added with the {@link #addfile(String, String)} method, or by passing an
 * array to the constructor of name,content pairs.
 *
 * Used for testing.
 *
 * @author Steve Ratcliffe
 */
public class StringStyleFileLoader extends StyleFileLoader {
	private final Map<String, String> files = new HashMap<String, String>();

	/**
	 * Pass filename and file contents like so:
	 * <pre>
	 *     new String[][] {
	 *         {"lines", "highway=primary [0x2]"},
	 *         {"points", "amenity=doctors [0x88]"},
	 *         ...
	 *     }
	 * </pre>
	 * @param files An array of filename, content pairs.
	 */
	public StringStyleFileLoader(String[][] files) {
		for (String[] nameContents : files)
			addfile(nameContents[0], nameContents[1]);
	}

	public void addfile(String name, String contents) {
		files.put(name, contents);
	}

	/**
	 * Open a file within the style. Creates a StringReader with the contents corresponding
	 * to the given filename. If the filename does not exist in the files array, then a FileNotFoundException
	 * is thrown as it would be in a regular style.
	 *
	 * @param filename The name of the file in the style.
	 * @return A StringReader with the contents of the file.
	 * @throws FileNotFoundException If the file name is not found in the files array.
	 */
	public Reader open(String filename) throws FileNotFoundException {
		String contents = files.get(filename);
		if (contents == null)
			throw new FileNotFoundException("No such file " + filename);
		return new StringReader(contents);
	}

	public void close() {
		// Nothing to do
	}

	/**
	 * List the filenames in the style.
	 * For completeness, we probably won't use this.
	 */
	public String[] list() {
		Set<String> strings = files.keySet();
		return strings.toArray(new String[strings.size()]);
	}
}
