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
 * Create date: Apr 13, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.log.Logger;

/**
 * Load a style from a single file. All the files that make up the style
 * are held in the same file and delimited by a simple header.
 *
 * <p>Lines before the first header are ignored.  A header looks like this:
 * &lt;&lt;&lt;filename>>>, that is three opening angle brackets, the name
 * of the file and three closing angle brackets.  The opening brackets
 * must be at the beginning of the line, there can be trailing junk after
 * the closing brackets which is ignored.
 *
 * <p>All lines after the header and before the next header or end of file
 * are part of the named file.
 *
 * <p>If there are no headers in the file, then we create a fake version 0
 * file and place the complete file in the name 'map-features.csv'.  This
 * allows us to wrap an existing map-features.csv file in the new style
 * system.
 * 
 * @author Steve Ratcliffe
 */
public class CombinedStyleFileLoader extends StyleFileLoader {
	private static final Logger log = Logger.getLogger(CombinedStyleFileLoader.class);

	private final Map<String, String> files = new HashMap<String, String>();
	private final String styleName;

	public CombinedStyleFileLoader(String filename) throws FileNotFoundException {
		styleName = filename.replaceFirst("\\.style$", "");

		Reader in = new FileReader(filename);

		loadFiles(in);
	}

	private void loadFiles(Reader in) {
		BufferedReader r = new BufferedReader(in);

		StringBuffer currentFile = new StringBuffer();
		try {
			String line;
			String currentName = null;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("<<<")) {
					if (currentName != null) {
						// Save previous file if any.
						files.put(currentName, currentFile.toString());
					}

					line = line.replaceFirst("<<<", "");
					line = line.replaceFirst(">>>.*", "");
					log.debug("reading file", line);
					currentName = line;
					currentFile = new StringBuffer();
				} else {
					currentFile.append(line);
					currentFile.append('\n');
				}
			}
			if (currentName == null) {
				// Special case, we have just got an old style map-features.csv
				// file to work with (or some other error).
				files.put("version", "0\n");
				files.put("map-features.csv", currentFile.toString());
				files.put("info",
						"description Style converted from map-features.csv file\n");
			} else {
				files.put(currentName, currentFile.toString());
			}
		} catch (IOException e) {
			log.error("failed to read style file");
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
		log.info("opening", file);
		String contents = files.get(file);
		if (contents == null)
			throw new FileNotFoundException(file);

		log.debug("file", file, "found");
		return new StringReader(contents);
	}

	/**
	 * Close the FileLoader.  This is different from closing individual files that
	 * were opened via {@link #open}.  After this call then you shouldn't open any
	 * more files.
	 */
	public void close() {
		files.clear();
	}

	public String[] list() {
		String basename = styleName.replaceFirst(".*[/\\\\]", "");
		basename = basename.replaceFirst("\\.[^.]+$", "");
		return new String[] {basename};
	}

	/**
	 * Covert between the single file simple-archive form and the directory
	 * form.  Mostly for fun.
	 *
	 * @param args Arguments, you supply a directory or a file.  If its a
	 * directory then covert into a simple-archive file and if it is a
	 * file then expand into separate files.
	 */
	public static void main(String[] args) {
		String name = args[0];
		File f = new File(name);

		PrintStream out = System.out;
		try {
			if (f.isDirectory()) {
				convertToFile(f, out);
			} else {
				String dirname;
				int ind = name.lastIndexOf('.');
				if (ind > 0)
					dirname = name.substring(0, ind);
				else
					dirname = name + ".d"; // got to do something...
				convertToDirectory(name, dirname);
			}
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file");
			System.exit(1);
		} catch (IOException e) {
			System.err.println("Could not read file");
			System.exit(1);
		}
	}

	private static void convertToDirectory(String name, String dirname) throws IOException {
		CombinedStyleFileLoader loader = new CombinedStyleFileLoader(name);
		File dir = new File(dirname);
		dir.mkdir();
		for (String s : loader.files.keySet()) {
			File ent = new File(dir, s);
			FileWriter writer = new FileWriter(ent);
			BufferedReader r = null;
			try {
				r = new BufferedReader(loader.open(s));
				String line;
				while ((line = r.readLine()) != null) {
					writer.write(line);
					writer.write('\n');
				}
			} finally {
				if (r != null) r.close();
				writer.close();
			}
		}
	}

	private static void convertToFile(File f, PrintStream out) throws IOException {
		File[] list = f.listFiles();
		for (File entry : list) {
			if (entry.isFile()) {
				out.println("<<<" + entry.getName() + ">>>");
				BufferedReader r = new BufferedReader(new FileReader(entry));
				String line;
				while ((line = r.readLine()) != null)
					out.println(line);
			}
		}
	}
}
