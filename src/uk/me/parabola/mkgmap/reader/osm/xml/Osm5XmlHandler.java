/*
 * Copyright (C) 2006 - 2012.
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

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GeneralRelation;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmHandler;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads and parses the OSM XML format.
 *
 * Creates the nodes/ways and relations that are read from the file and passes
 * them to the OsmCollector.
 *
 * It should not examine tags, or do anything else.
 *
 * @author Steve Ratcliffe
 */
public class Osm5XmlHandler extends OsmHandler {
	private static final Logger log = Logger.getLogger(Osm5XmlHandler.class);

	// Set to the currently processing element.
	private int mode;

	// Values for mode above.
	private static final int MODE_NODE = 1;
	private static final int MODE_WAY = 2;
	private static final int MODE_BOUND = 3;
	private static final int MODE_RELATION = 4;
	private static final int MODE_BOUNDS = 5;

	// Options
	private final boolean ignoreBounds;
	// Current state.
	protected Node currentNode;
	protected Way currentWay;
	protected Relation currentRelation;
	protected long currentElementId;

	public Osm5XmlHandler(EnhancedProperties props) {
		ignoreBounds = props.getProperty("ignore-osm-bounds", false);
	}

	/**
	 * The XML handler callbacks.
	 *
	 * Need an inner class here so that the top class can inherit from OsmHandler.
	 */
	public class SaxHandler extends DefaultHandler {

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
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (mode == 0) {
				if (qName.equals("node")) {
					mode = MODE_NODE;
					startNode(attributes.getValue("id"),
							attributes.getValue("lat"),
							attributes.getValue("lon"));

				} else if (qName.equals("way")) {
					mode = MODE_WAY;
					startWay(attributes.getValue("id"));

				} else if (qName.equals("relation")) {
					mode = MODE_RELATION;
					currentRelation = new GeneralRelation(idVal(attributes.getValue("id")));

				} else if (qName.equals("bound")) {
					mode = MODE_BOUND;
					if(!ignoreBounds) {
						String box = attributes.getValue("box");
						setupBBoxFromBound(box);
					}

				} else if (qName.equals("bounds")) {
					mode = MODE_BOUNDS;
					if(!ignoreBounds)
						setupBBoxFromBounds(attributes);
				}

			} else if (mode == MODE_NODE) {
				startInNode(qName, attributes);
			} else if (mode == MODE_WAY) {
				startInWay(qName, attributes);
			} else if (mode == MODE_RELATION) {
				startInRelation(qName, attributes);
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
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (mode == MODE_NODE) {
				if (qName.equals("node")) {
					mode = 0;
					if (currentNode != null) {
						saver.addNode(currentNode);
						hooks.onAddNode(currentNode);
					}
					currentElementId = 0;
					currentNode = null;
				}

			} else if (mode == MODE_WAY) {
				if (qName.equals("way")) {
					mode = 0;

					endWay(currentWay);
					currentWay = null;
				}

			} else if (mode == MODE_BOUND) {
				if (qName.equals("bound"))
					mode = 0;

			} else if (mode == MODE_BOUNDS) {
				if (qName.equals("bounds"))
					mode = 0;

			} else if (mode == MODE_RELATION) {
				if (qName.equals("relation")) {
					mode = 0;
					
					// remove the mkgmap:tagsincomplete tags which is used in multipolygons only
					if (currentRelation.getTag(TAGS_INCOMPLETE_TAG) != null) {
						String type = currentRelation.getTag("type");
						if ("multipolygon".equals(type) == false && "boundary".equals(type) == false) {
							currentRelation.deleteTag(TAGS_INCOMPLETE_TAG);
						}
					}
					
					saver.addRelation(currentRelation);
				}
			}
		}

		/**
		 * Called on an XML error.  Attempt to print a line number to aid in
		 * working out the problem.
		 * @throws SAXException
		 */
		public void fatalError(SAXParseException e) throws SAXException {
			System.err.println("Error at line " + e.getLineNumber() + ", col "
					+ e.getColumnNumber());
			super.fatalError(e);
		}
	}

	/**
	 * A new tag has been started while we are inside a node element.
	 * @param qName The new tag name.
	 * @param attributes Its attributes.
	 */
	private void startInNode(String qName, Attributes attributes) {
		if (qName.equals("tag")) {
			String key = attributes.getValue("k");
			String val = attributes.getValue("v");

			// We only want to create a full node for nodes that are POI's
			// and not just one point of a way.  Only create if it has tags that
			// could be used in a POI.
			key = keepTag(key, val);
			if (key != null) {
				if (currentNode == null) {
					Coord co = saver.getCoord(currentElementId);
					currentNode = new Node(currentElementId, co);
				}

				currentNode.addTag(key, val.intern());
			}
		}
	}

	/**
	 * A new tag has been started while we are inside a way element.
	 * @param qName The new tag name.
	 * @param attributes Its attributes.
	 */
	private void startInWay(String qName, Attributes attributes) {
		if (qName.equals("nd")) {
			long id = idVal(attributes.getValue("ref"));
			addCoordToWay(currentWay, id);
		} else if (qName.equals("tag")) {
			String key = attributes.getValue("k");
			String val = attributes.getValue("v");
			key = keepTag(key, val);
			if (key != null)
				currentWay.addTag(key, val.intern());
		}
	}

	/**
	 * A new tag has been started while we are inside the relation tag.
	 * @param qName The new tag name.
	 * @param attributes Its attributes.
	 */
	private void startInRelation(String qName, Attributes attributes) {
		if (qName.equals("member")) {
			long id = idVal(attributes.getValue("ref"));
			Element el;
			String type = attributes.getValue("type");
			if ("way".equals(type)){
				el = saver.getWay(id);
			} else if ("node".equals(type)) {
				el = saver.getNode(id);
				if(el == null) {
					// we didn't make a node for this point earlier,
					// do it now (if it exists)
					Coord co = saver.getCoord(id);
					if(co != null) {
						el = new Node(id, co);
						saver.addNode((Node)el);
					}
				}
			} else if ("relation".equals(type)) {
				el = saver.getRelation(id);
				if (el == null) {
					saver.deferRelation(id, currentRelation, attributes.getValue("role"));
				}
			} else
				el = null;
			if (el != null) // ignore non existing ways caused by splitting files
				currentRelation.addElement(attributes.getValue("role"), el);
		} else if (qName.equals("tag")) {
			String key = attributes.getValue("k");
			String val = attributes.getValue("v");
			// the type tag is required for relations - all other tags are filtered
			if ("type".equals(key))
				// intern the key
				key = "type";
			else
				key = keepTag(key, val);
			if (key == null) {
				currentRelation.addTag(TAGS_INCOMPLETE_TAG, "true");
			} else {
				currentRelation.addTag(key, val.intern());
			}
		}
	}

	/**
	 * Set a bounding box from the bounds element.
	 * There are two ways of specifying a bounding box in the XML format, this
	 * one uses attributes of the element to give the bounds.
	 * @param xmlattr The bounds element attributes.
	 */
	private void setupBBoxFromBounds(Attributes xmlattr) {
		try {
			setBBox(Double.parseDouble(xmlattr.getValue("minlat")),
					Double.parseDouble(xmlattr.getValue("minlon")),
					Double.parseDouble(xmlattr.getValue("maxlat")),
					Double.parseDouble(xmlattr.getValue("maxlon")));
		} catch (NumberFormatException e) {
			// just ignore it
			log.warn("NumberformatException: Cannot read bbox");
		}
	}

	/**
	 * Set a bounding box from the bound element.  There are two ways of
	 * specifying a bounding box, this one has a single 'box' attribute that
	 * is a comma separated list of the bounds values.
	 * @param box The value of the box attribute.
	 */
	private void setupBBoxFromBound(String box) {
		String[] f = box.split(",");
		try {
			setBBox(Double.parseDouble(f[0]), Double.parseDouble(f[1]),
					Double.parseDouble(f[2]), Double.parseDouble(f[3]));
		} catch (NumberFormatException e) {
			// just ignore it
			log.warn("NumberformatException: Cannot read bbox");
		}
	}

	/**
	 * Save node information.  Consists of a location specified by lat/long.
	 *
	 * @param sid The id as a string.
	 * @param slat The lat as a string.
	 * @param slon The longitude as a string.
	 */
	private void startNode(String sid, String slat, String slon) {
		if (sid == null || slat == null || slon == null)
			return;
		
		try {
			long id = idVal(sid);

			Coord co = new Coord(Double.parseDouble(slat), Double.parseDouble(slon));
			saver.addPoint(id, co);
			currentElementId = id;
		} catch (NumberFormatException e) {
			// ignore bad numeric data. The coord will be discarded
		}
	}

	/**
	 * A new way element has been seen.
	 * @param sid The way id as a string.
	 */
	private void startWay(String sid) {
		try {
			long id = idVal(sid);
			currentWay = startWay(id);
		} catch (NumberFormatException e) {
			// ignore bad numeric data. The way will be discarded
		}
	}
}
