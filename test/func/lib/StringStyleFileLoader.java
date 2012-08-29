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
 * For testing.
 *
 * @author Steve Ratcliffe
 */
public class StringStyleFileLoader extends StyleFileLoader {
	private final Map<String, String> files = new HashMap<String, String>();

	public StringStyleFileLoader(String[][] files) {
		for (String[] nameContents : files)
			addfile(nameContents[0], nameContents[1]);
	}

	public void addfile(String name, String contents) {
		files.put(name, contents);
	}

	public Reader open(String filename) throws FileNotFoundException {
		String contents = files.get(filename);
		if (contents == null)
			throw new FileNotFoundException("No such file " + filename);
		return new StringReader(contents);
	}

	public void close() {
		// Nothing to do
	}

	public String[] list() {
		Set<String> strings = files.keySet();
		return strings.toArray(new String[strings.size()]);
	}
}
