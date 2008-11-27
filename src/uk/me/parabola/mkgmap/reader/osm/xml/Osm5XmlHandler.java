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

import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads and parses the OSM XML format.
 *
 * @author Steve Ratcliffe
 */
class Osm5XmlHandler extends DefaultHandler {
	private static final Logger log = Logger.getLogger(Osm5XmlHandler.class);
	
	private int mode;

	private final Map<Long, Coord> nodeMap = new HashMap<Long, Coord>();
	private final Map<Long, Way> wayMap = new HashMap<Long, Way>();

	private static final int MODE_NODE = 1;
	private static final int MODE_WAY = 2;
	private static final int MODE_BOUND = 3;
	private static final int MODE_RELATION = 4;
	private static final int MODE_BOUNDS = 5;

	private Node currentNode;
	private Way currentWay;
	private Relation currentRelation;

	private OsmConverter converter;
	private MapCollector mapper;
	private long currentNodeId;
	private Area bbox;

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
				addWay(attributes.getValue("id"));
			} else if (qName.equals("relation")) {
				mode = MODE_RELATION;
				currentRelation = new Relation();		
			} else if (qName.equals("bound")) {
				mode = MODE_BOUND;
				String box = attributes.getValue("box");
				setupBBoxFromBound(box);
			} else if (qName.equals("bounds")) {
				mode = MODE_BOUNDS;
				setupBBoxFromBounds(attributes);
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
		} else if (mode == MODE_RELATION) {
			if (qName.equals("member")) {
				if (attributes.getValue("type").equals("way")){
				long id = Long.parseLong(attributes.getValue("ref"));
				String role = attributes.getValue("role");
				Way way = wayMap.get(id);
				if (way != null) // ignore non existing ways caused by splitting files 
				  currentRelation.addWay( role, way); 		
				}
			} else if (qName.equals("tag")) {
				String key = attributes.getValue("k");
				String val = attributes.getValue("v");
				currentRelation.addTag(key, val);
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
				if (currentNode != null) {
					converter.convertName(currentNode);
					converter.convertNode(currentNode);
				}
				currentNodeId = 0;
				currentNode = null;
			}

		} else if (mode == MODE_WAY) {
			if (qName.equals("way")) {
				mode = 0;
				currentWay = null;
				// ways are processed at the end of the document,
				// may be changed by a Relation class
			}
		} else if (mode == MODE_BOUND) {
			if (qName.equals("bound")) {
				mode = 0;
			}
		} else if (mode == MODE_BOUNDS) {
			if (qName.equals("bounds")) {
				mode = 0;
			}
		} else if (mode == MODE_RELATION) {
			if (qName.equals("relation")) {
				mode = 0;
				currentRelation.processWays();
				currentRelation = null;
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
		for (Way w: wayMap.values()){
			converter.convertName(w);			
			converter.convertWay(w);				
		}
		mapper.finish();
	}

	private void setupBBoxFromBounds(Attributes xmlattr) {
		try {
			double minlat = Double.parseDouble(xmlattr.getValue("minlat"));
			double minlon = Double.parseDouble(xmlattr.getValue("minlon"));
			double maxlat = Double.parseDouble(xmlattr.getValue("maxlat"));
			double maxlon = Double.parseDouble(xmlattr.getValue("maxlon"));

			setBBox(minlat, minlon, maxlat, maxlon);
		} catch (NumberFormatException e) {
			// just ignore it
		}
	}

	private void setupBBoxFromBound(String box) {
		String[] f = box.split(",");
		try {
			double minlat = Double.parseDouble(f[0]);
			double minlong = Double.parseDouble(f[1]);
			double maxlat = Double.parseDouble(f[2]);
			double maxlong = Double.parseDouble(f[3]);

			setBBox(minlat, minlong, maxlat, maxlong);
			log.debug("Map bbox: " + bbox);
		} catch (NumberFormatException e) {
			// just ignore it
		}
	}

	private void setBBox(double minlat, double minlong,
	                     double maxlat, double maxlong) {

		bbox = new Area(minlat, minlong, maxlat, maxlong);
		converter.setBoundingBox(bbox);

		Coord co = new Coord(minlat, minlong);
		mapper.addToBounds(co);
		co = new Coord(minlat, maxlong);
		mapper.addToBounds(co);
		co = new Coord(maxlat, minlong);
		mapper.addToBounds(co);
		co = new Coord(maxlat, maxlong);
		mapper.addToBounds(co);
	}

	/**
	 * Save node information.  Consists of a location specified by lat/long.
	 *
	 * @param sid The id as a string.
	 * @param slat The lat as a string.
	 * @param slon The longitude as a string.
	 */
	private void addNode(String sid, String slat, String slon) {
		try {
			long id = Long.parseLong(sid);
			double lat = Double.parseDouble(slat);
			double lon = Double.parseDouble(slon);

			Coord co = new Coord(lat, lon);
			nodeMap.put(id, co);
			currentNodeId = id;
			if (bbox == null)
				mapper.addToBounds(co);
		} catch (NumberFormatException e) {
			// ignore bad numeric data.
		}
	}
	
	private void addWay(String sid) {
		try {
			currentWay = new Way();
			long id = Long.parseLong(sid);	 
			wayMap.put(id, currentWay);	
		} catch (NumberFormatException e) {
			// ignore bad numeric data.
		}
	}
	
	private void addNodeToWay(long id) {
		Coord co = nodeMap.get(id);
		if (co != null)
			currentWay.addPoint(co);
	}

	public void setCallbacks(MapCollector mapCollector) {
		mapper = mapCollector;
	}

	public void setConverter(OsmConverter converter) {
		this.converter = converter;
	}

	public void fatalError(SAXParseException e) throws SAXException {
		System.err.println("Error at line " + e.getLineNumber() + ", col "
				+ e.getColumnNumber());
		super.fatalError(e);
	}
}
