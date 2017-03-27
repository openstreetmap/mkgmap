/*
 * Copyright (C) 2010 - 2012.
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

package uk.me.parabola.mkgmap.reader.osm.xml;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.mkgmap.reader.osm.OsmMapDataSource;

import org.xml.sax.SAXException;


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

	@Override
	public boolean isFileSupported(String name) {
		// This is the default format so say supported if we get this far,
		// this one must always be last for this reason.
		return true;
	}

	@Override
	public void load(InputStream is) throws FormatException {
		try {
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			parserFactory.setXIncludeAware(true);
			parserFactory.setNamespaceAware(true);
			SAXParser parser = parserFactory.newSAXParser();

			try {
				Osm5XmlHandler handler = new Osm5XmlHandler(getConfig());
				Osm5XmlHandler.SaxHandler saxHandler = handler.new SaxHandler();

				setupHandler(handler);
				handler = null;
				
				// parse the xml file
				parser.parse(is, saxHandler);

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
