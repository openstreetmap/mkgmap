/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.reader.osm;

import org.xml.sax.SAXException;
import uk.me.parabola.imgfmt.FormatException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


/**
 * Read an OpenStreetMap data file in .osm version 0.5 format.  It is converted
 * into a generic format that the map is built from.
 * <p>The intermediate format is important as several passes are required to
 * produce the map at different zoom levels. At lower resolutions, some roads
 * will have fewer points or won't be shown at all.
 *
 * @author Steve Ratcliffe
 */
public class Osm5MapDataSource extends OsmMapDataSource {

	public boolean isFileSupported(String name) {
		FileReader r = null;
		try {
			r = new FileReader(name);
			char[] buf = new char[1025];
			r.read(buf);
			String s = new String(buf);
            if (!s.startsWith("<?xml"))
                return false;
            if (!s.contains("<osm"))
                return false;
			if (s.contains("version='0.5'") || s.contains("version=\"0.5\""))
				return true;
			return false;
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		} finally {
            try {
                if (r != null)
                    r.close();
            } catch (IOException e) {
                // go away.
			}
		}
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		try {
			FileInputStream is = new FileInputStream(name);
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			SAXParser parser = parserFactory.newSAXParser();

			try {
				Osm5XmlHandler handler = new Osm5XmlHandler();
				handler.setCallbacks(mapper);
				handler.setConverter(new FeatureListConverter(mapper, getConfig()));
				parser.parse(is, handler);
			} catch (IOException e) {
				throw new FormatException("Error reading file", e);
			}
		} catch (SAXException e) {
			throw new FormatException("Error parsing file", e);
		} catch (ParserConfigurationException e) {
			throw new FormatException("Internal error configuring xml parser", e);
		}
	}
}