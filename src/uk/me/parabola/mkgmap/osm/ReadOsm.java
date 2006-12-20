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
package uk.me.parabola.mkgmap.osm;

import uk.me.parabola.mkgmap.MapSource;
import uk.me.parabola.mkgmap.MapCollector;
import uk.me.parabola.mkgmap.FormatException;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;

import org.xml.sax.SAXException;


/**
 * Read an OpenStreetMap data file in .osm format.
 *
 * @author Steve Ratcliffe
 */
public class ReadOsm implements MapSource {

	private MapCollector mapper;

	public void setMapCollector(MapCollector mapper) {
		this.mapper = mapper;
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		try {
			FileInputStream is= new FileInputStream(name);
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			SAXParser parser = parserFactory.newSAXParser();

			try {
				OSMXmlHandler handler = new OSMXmlHandler();
				handler.setCallbacks(mapper);
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
