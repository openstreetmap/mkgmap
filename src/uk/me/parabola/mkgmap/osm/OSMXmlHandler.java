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

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapCollector;

/**
 * Reads and parses the OSM XML format.
 *
 * @author Steve Ratcliffe
 */
class OSMXmlHandler extends DefaultHandler {
	private static final Logger log = Logger.getLogger(OSMXmlHandler.class);

	private MapCollector mapper;
	private OsmConverter converter;

	private int mode;

	private final Map<Long, Coord> nodeMap = new HashMap<Long, Coord>();
	private final Map<Long, Segment> segMap = new HashMap<Long, Segment>();

	private Way currentWay;

	private static final int MODE_NODE = 1;
	private static final int MODE_SEGMENT = 2;
	private static final int MODE_WAY = 3;

	/**
	 * Receive notification of the start of an element.
	 * <p/>

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
			} else if (qName.equals("segment")) {
				mode = MODE_SEGMENT;

				String id = attributes.getValue("id");
				String from = attributes.getValue("from");
				String to = attributes.getValue("to");

				addSegment(id, from, to);
			} else if (qName.equals("way")) {
				mode = MODE_WAY;
				currentWay = new Way();
			}
		} else if (mode == MODE_NODE) {
			// We are not interested in anything under node.
		} else if (mode == MODE_SEGMENT) {
			// not yet interested in anything here.
		} else if (mode == MODE_WAY) {
			if (qName.equals("seg")) {
				long id = Long.parseLong(attributes.getValue("id"));
				addSegmentToWay(id);
			} else if (qName.equals("tag")) {
				String key = attributes.getValue("k");
				String val = attributes.getValue("v");
				addTagToWay(key, val);

			}
		}
	}

	/**
	 * Receive notification of the end of an element.
	 * <p/>

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
			if (qName.equals("node"))
				mode = 0;
		} else if (mode == MODE_SEGMENT) {
			if (qName.equals("segment"))
				mode = 0;
		} else if (mode == MODE_WAY) {
			if (qName.equals("way")) {
				mode = 0;
				// Process the way.
				converter.processWay(currentWay);
			}
		}
	}

	/**
	 * Receive notification of the end of the document.
	 *
	 * @throws SAXException Any SAX exception, possibly
	 *                                  wrapping another exception.
	 * @see ContentHandler#endDocument
	 */
	public void endDocument() throws SAXException {
		super.endDocument();
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

		if (log.isDebugEnabled())
			log.debug("adding node" + lat + '/' + lon);
		nodeMap.put(id, new Coord(lat, lon)); // TODO: need a proper Node type
	}

	/**
	 * Save a segment.  Fetch the nodes and save the coordinates as part of
	 * the segment definition.
	 * All inputs are as strings.
	 *
	 * @param sid The id as a string.
	 * @param sfrom The from node as a string.
	 * @param sto the to node as a string.
	 */
	private void addSegment(String sid, String sfrom, String sto) {
		long id = Long.parseLong(sid);
		long from = Long.parseLong(sfrom);
		long to = Long.parseLong(sto);

		Coord start = nodeMap.get(from);
		Coord end = nodeMap.get(to);

		mapper.addToBounds(start);

		if (log.isDebugEnabled())
		log.debug("adding segment " + start + " to " + end);
		Segment seg = new Segment(id, start, end);
		segMap.put(id, seg);
	}

	private void addTagToWay(String key, String val) {
		currentWay.addTag(key, val);
	}

	private void addSegmentToWay(long id) {
		Segment seg = segMap.get(id);
		currentWay.addSegment(seg);
	}

	public void setCallbacks(MapCollector mapCollector) {
		mapper = mapCollector;
		converter = new OsmConverter(mapCollector);
	}
}
