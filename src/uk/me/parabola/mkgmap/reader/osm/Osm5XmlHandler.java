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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and parses the OSM XML format.
 *
 * @author Steve Ratcliffe
 */
class Osm5XmlHandler extends DefaultHandler {
	private int mode;

	private final Map<Long, Coord> nodeMap = new HashMap<Long, Coord>();

	private static final int MODE_NODE = 1;
	private static final int MODE_WAY = 2;

	private Node currentNode;
	private Way5 currentWay;

	private FeatureListConverter converter;
	private MapCollector mapper;
	private long currentNodeId;

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

				// We only want to create a full node for nodes that are POI's
				// and not just point of a way.  Only create if it has tags that
				// are not in a list of ignorables ones such as 'created_by'
				if (currentNode != null || !key.equals("created_by")) {
					if (currentNode == null) {
						Coord co = nodeMap.get(currentNodeId);
						currentNode = new Node(currentNodeId, co);
					}
					currentNode.addTag(key, val);
                }
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
				if (currentNode != null)
					converter.convertNode(currentNode);
				currentNodeId = 0;
				currentNode = null;
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
	 * Receive notification of the end of the document.
	 *
	 * We add the background polygon here.  As this is going to be big it
	 * may be split up further down the chain.
	 *
	 * @throws SAXException Any SAX exception, possibly wrapping
	 * another exception.
	 */
	public void endDocument() throws SAXException {
		mapper.finish();
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
		currentNodeId = id;
		mapper.addToBounds(co);
	}

	private void addNodeToWay(long id) {
		Coord co = nodeMap.get(id);
		if (co != null)
			currentWay.addPoint(co);
	}

	public void setCallbacks(MapCollector mapCollector) {
		mapper = mapCollector;
	}

	public void setConverter(FeatureListConverter converter) {
		this.converter = converter;
	}

	public void fatalError(SAXParseException e) throws SAXException {
		System.err.println("Error at line " + e.getLineNumber() + ", col "
				+ e.getColumnNumber());
		super.fatalError(e);
	}
}