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
 * Create date: 31-Dec-2006
 */
package uk.me.parabola.mkgmap.osm;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.main.ExitException;
import uk.me.parabola.imgfmt.app.Coord;

import java.io.*;
import java.util.*;

/**
 * Reads in a CSV file from the OSMGarminMap project that contains a list
 * of OSM map features and their corresponding Garmin map feature numbers.
 *
 * @author Steve Ratcliffe
 */
public class FeatureListConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(FeatureListConverter.class);

	private static final String FEATURE_LIST_NAME = "feature_map.csv";

	private static final int F_FEATURE_TYPE = 0;
	private static final int F_OSM_TYPE = 1;
	private static final int F_OSM_SUBTYPE = 2;
	private static final int F_GARMIN_TYPE = 3;
	private static final int F_GARMIN_SUBTYPE = 4;

	private static final int N_FIELDS = 5;

//	private Map pointFeatures = new HashMap();

	private Map<String, GarminType> pointFeatures = new HashMap<String, GarminType>();
	private Set<String> pointTypes = new HashSet<String>();

	private Map<String, GarminType> lineFeatures = new HashMap<String, GarminType>();
	private Set<String> lineTypes = new HashSet<String>();

	private Map<String, GarminType> shapeFeatures = new HashMap<String, GarminType>();
	private Set<String> shapeTypes = new HashSet<String>();

	private MapCollector mapper;

	public FeatureListConverter(MapCollector collector) {
		this.mapper = collector;

		InputStream is = ClassLoader.getSystemResourceAsStream(FEATURE_LIST_NAME);
		if (is == null)
			throw new ExitException("Could not find feature list resource");

		try {
			Reader r = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(r);

			readFeatures(br);

		} catch (UnsupportedEncodingException e) {
			log.error("reading features failed");
			e.printStackTrace();
		} catch (IOException e) {
			log.error("reading features failed");
			e.printStackTrace();
		}
	}

	private void readFeatures(BufferedReader in) throws IOException {
		log.info("reading features");
		String line;
		while ((line = in.readLine()) != null) {
			String[] fields = line.split("\\|", -1);
			if (fields.length != N_FIELDS) {
				continue;
			}

			String type = fields[F_FEATURE_TYPE];
			log.debug("feature kind " + type);
			if (type.equals("point")) {
				log.debug("point type found");
				saveFeature(fields, pointTypes, pointFeatures);

			} else if (type.equals("polyline")) {
				log.debug("polyline type found");
				// Lines only have types and not subtypes on
				// the garmin side
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(fields, lineTypes, lineFeatures);

			} else if (type.equals("polygon")) {
				log.debug("polygon type found");
				saveFeature(fields, shapeTypes, shapeFeatures);

			} else {
				// Unknown type
				log.warn("unknown feature type " + type);
			}
		}
	}

	private void saveFeature(String[] fields, Set<String> types, Map<String, GarminType> features) {
		String osm = makeKey(fields[F_OSM_TYPE], fields[F_OSM_SUBTYPE]);
		types.add(fields[F_OSM_TYPE]);

		GarminType gtype = new GarminType(fields[F_GARMIN_TYPE]);
		features.put(osm, gtype);
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p/>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		// Try all the types we know about
		for (String t : lineTypes) {
			String val = way.getTag(t);
			if (val != null) {
				// Found it! Now add to the map.
				GarminType gt = lineFeatures.get(makeKey(t, val));
				if (gt == null)
					continue;
				List<List<Coord>> pointLists =  way.getPoints();
				for (List<Coord> points : pointLists) {
					MapLine line = new MapLine();
					line.setName(way.getName());
					line.setPoints(points);
					line.setType(gt.getType());

					mapper.addLine(line);
				}
				return;
			}
		}

		// OK if we get here, it might be a polygon instead. Its not really
		// possible to say without checking.
		for (String t : shapeTypes) {
			String val = way.getTag(t);
			if (val != null) {
				// Add to the map
				GarminType gt = shapeFeatures.get(makeKey(t, val));
				if (gt == null)
					continue;
				List<List<Coord>> pointLists =  way.getPoints();
				for (List<Coord> points : pointLists) {
					MapShape shape = new MapShape();
					shape.setName(way.getName());
					shape.setPoints(points);
					shape.setType(gt.getType());

					mapper.addShape(shape);
				}
				return;
			}
		}
	}

	private String makeKey(String key, String val) {
		return key + '|' + val;
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		for (String t : pointTypes) {
			String val = node.getTag(t);
			if (val != null) {
				// Add to the map
				GarminType gt = pointFeatures.get(makeKey(t, val));
				if (gt == null)
					continue;
				MapPoint point = new MapPoint();
				point.setName(node.getName());
				point.setLocation(node.getLocation());
				point.setType(gt.getType());
				point.setSubType(gt.getSubtype());

				mapper.addPoint(point);
				return;
			}
		}
	}

	static class GarminType {
		private static final Logger log = Logger.getLogger(GarminType.class);

		private int type;
		private int subtype;

		public GarminType(String type) {
			try {
				this.type = Integer.decode(type);
			} catch (NumberFormatException e) {
				log.debug("not numeric ", type, subtype);
				this.type = 0;
			}
		}

		public GarminType(String type, String subtype) {
			try {
				this.type = Integer.decode(type);
				this.subtype = Integer.decode(subtype);
			} catch (NumberFormatException e) {
				log.debug("not numeric ", type, subtype);
			}

		}

		/**
		 * Returns a hash code value for the object.
		 *
		 * @return a hash code value for this object.
		 * @see Object#equals(Object)
		 */
		public int hashCode() {
			return type + subtype;
		}

		/**
		 * Indicates whether some other object is "equal to" this one.
		 * <p/>
		 * The <code>equals</code> method implements an equivalence relation
		 * on non-null object references:
		 *
		 *
		 * @param obj the reference object with which to compare.
		 * @return <code>true</code> if this object is the same as the obj
		 *         argument; <code>false</code> otherwise.
		 * @see #hashCode()
		 */
		public boolean equals(Object obj) {
			if (obj instanceof GarminType) {
				GarminType gt = (GarminType) obj;
				if (gt.type == type && gt.subtype == subtype) {
					return true;
				}
			}

			return false;
		}

		public int getType() {
			return type;
		}

		public int getSubtype() {
			return subtype;
		}
	}
}
