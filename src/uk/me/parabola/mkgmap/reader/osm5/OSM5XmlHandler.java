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
package uk.me.parabola.mkgmap.reader.osm5;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.reader.osm.FeatureListConverter;
import uk.me.parabola.mkgmap.reader.osm.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and parses the OSM XML format.
 *
 * @author Steve Ratcliffe
 */
class OSM5XmlHandler extends DefaultHandler {
	private int mode;

	private Map<Long, Coord> nodeMap = new HashMap<Long, Coord>();

	private static final int MODE_NODE = 1;
	private static final int MODE_WAY = 2;

	private Node currentNode;
	private Way5 currentWay;

	private FeatureListConverter converter;
	private MapCollector mapper;

	/**
	 * Receive notification of the start of an element.
	 *
	 * @param uri		The Namespace URI, or the empty string if the
	 *                   element has no Namespace URI or if Namespace
	 *                   processing is not being performed.
	 * @param localName  The local name (without prefix), or the
	 *                   empty string if Namespace processing is not being
	 *                   performed.
	 * @param qName	  The qualified name (with prefix), or the
	 *                   empty string if qualified names are not available.
	 * @param attributes The attributes attached to the element.  If
	 *                   there are no attributes, it shall be an empty
	 *                   Attributes object.
	 * @throws SAXException Any SAX exception, possibly
	 *                                  wrapping another exception.
	 * @see ContentHandler#startElement
	 */
	public void startElement(String uri, String localName,
	                         String qName, Attributes attributes)
			throws SAXException
	{

		if (mode == 0) {
			if (qName.equals("node")) {
				mode = MODE_NODE;

				String id = attributes.getValue("id");
				String lat = attributes.getValue("lat");
				String lon = attributes.getValue("lon");

				addNode(id, lat, lon);

			} else if (qName.equals("way")) {
				mode = MODE_WAY;
				currentWay = new Way5();
			}
		} else if (mode == MODE_NODE) {
			if (qName.equals("tag")) {
				String key = attributes.getValue("k");
				String val = attributes.getValue("v");
				//addTagToNode(key, val);
				currentNode.addTag(key, val);
			}

		} else if (mode == MODE_WAY) {
			if (qName.equals("nd")) {
				long id = Long.parseLong(attributes.getValue("ref"));
				addNodeToWay(id);
			} else if (qName.equals("tag")) {
				String key = attributes.getValue("k");
				String val = attributes.getValue("v");
				currentWay.addTag(key, val);
			}
		}
	}

	private void addNodeToWay(long id) {
		Coord co = nodeMap.get(id);
		currentWay.addPoint(co);
	}

	/**
	 * Receive notification of the end of an element.
	 *
	 * @param uri	   The Namespace URI, or the empty string if the
	 *                  element has no Namespace URI or if Namespace
	 *                  processing is not being performed.
	 * @param localName The local name (without prefix), or the
	 *                  empty string if Namespace processing is not being
	 *                  performed.
	 * @param qName	 The qualified name (with prefix), or the
	 *                  empty string if qualified names are not available.
	 * @throws SAXException Any SAX exception, possibly
	 *                                  wrapping another exception.
	 * @see ContentHandler#endElement
	 */
	public void endElement(String uri, String localName, String qName)
			throws SAXException
	{
		if (mode == MODE_NODE) {
			if (qName.equals("node")) {
				mode = 0;
				// TODO: only do this when it is likely to be required
				converter.convertNode(currentNode);
			}

		} else if (mode == MODE_WAY) {
			if (qName.equals("way")) {
				mode = 0;
				// Process the way.
				converter.convertWay(currentWay);
			}
		}
	}

	/**
	 * Save node information.  Consists of a location specified by lat/long.
	 *
	 * @param sid The id as a string.
	 * @param slat The lat as a string.
	 * @param slon The longitude as a string.
	 */
	private void addNode(String sid, String slat, String slon) {
		long id = Long.parseLong(sid);
		double lat = Double.parseDouble(slat);
		double lon = Double.parseDouble(slon);

		//if (log.isDebugEnabled())
		//	log.debug("adding node" + lat + '/' + lon);
		Coord co = new Coord(lat, lon);
		nodeMap.put(id, co);
		currentNode = new Node(id, co);
		mapper.addToBounds(co);
	}

	public void setCallbacks(MapCollector mapCollector) {
		mapper = mapCollector;
		converter = new FeatureListConverter(mapCollector);
	}
}