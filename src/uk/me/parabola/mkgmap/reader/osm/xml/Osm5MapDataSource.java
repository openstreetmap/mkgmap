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
package uk.me.parabola.mkgmap.reader.osm.xml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Style;

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

	public boolean isFileSupported(String name) {
		// This is the default format so say supported if we get this far,
		// this one must always be last for this reason.
		return true;
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		try {
			InputStream is = openFile(name);
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			SAXParser parser = parserFactory.newSAXParser();

			try {
				Osm5XmlHandler handler = new Osm5XmlHandler();
				handler.setCallbacks(mapper);
				handler.setConverter(createStyler());
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

	/**
	 * Create the appropriate converter from osm to garmin styles.
	 *
	 * The option --style-file give the location of an alternate file or
	 * directory containing styles rather than the default built in ones.
	 *
	 * The option --style gives the name of a style, either one of the
	 * built in ones or selects one from the given style-file.
	 *
	 * If there is no name given, but there is a file then the file should
	 * just contain one style.
	 *
	 * @return An OsmConverter based on the command line options passed in.
	 */
	private OsmConverter createStyler() {

		Properties props = getConfig();
		String loc = props.getProperty("style-file");
		if (loc == null)
			loc = props.getProperty("map-features");
		String name = props.getProperty("style");

		if (loc == null && name == null)
			name = "default";

		OsmConverter converter;
		try {
			// TODO: Move this routine, so that Style implementation reference is outside this package
			Style style = new StyleImpl(loc, name);
			style.applyOptionOverride(props);
			setStyle(style);

			converter = new StyledConverter(style, mapper);
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
			throw new ExitException("Could not open style " + name);
		} catch (FileNotFoundException e) {
			String name1 = (name != null)? name: loc;
			throw new ExitException("Could not open style " + name1);
		}

		return converter;
	}
}