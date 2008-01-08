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
package uk.me.parabola.mkgmap.reader.osm;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads in a CSV file from the OSMGarminMap project that contains a list
 * of OSM map features and their corresponding Garmin map feature numbers.
 *
 * @author Steve Ratcliffe
 */
class FeatureListConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(FeatureListConverter.class);

	// File names
	private static final String FEATURE_LIST_NAME = "map-features.csv";
	private static final String OLD_FEATURE_LIST_NAME = "feature_map.csv";

	// Specification of the fields in the map-features file.
	private static final int F_FEATURE_TYPE = 0;
	private static final int F_OSM_TYPE = 1;
	private static final int F_OSM_SUBTYPE = 2;
	private static final int F_GARMIN_TYPE = 3;
	private static final int F_GARMIN_SUBTYPE = 4;
	private static final int F_MIN_RESOLUTION = 5;

	private static final int N_MIN_FIELDS = 5;

	private static final int DEFAULT_RESOLUTION = 24;

	// Maps of osm feature names to the garmin type information
	private final Map<String, GarminType> pointFeatures = new HashMap<String, GarminType>();
	private final Map<String, GarminType> lineFeatures = new HashMap<String, GarminType>();
	private final Map<String, GarminType> shapeFeatures = new HashMap<String, GarminType>();

	// Collector for saving the created features.
	private final MapCollector mapper;

	FeatureListConverter(MapCollector collector, Properties config) {
		this.mapper = collector;

		InputStream is = getMapFeatures(config);

		try {
			Reader r = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(r);

			readFeatures(br);

		} catch (UnsupportedEncodingException e) {
			log.error("reading features failed");
		} catch (IOException e) {
			log.error("reading features failed");
		}
	}

	/**
	 * Get an input stream to a map-features file.  This could have been on the
	 * command line, in which case return an open handle to it.
	 *
	 * @param config Properties that may contain the name of a file to use.
	 * @return An open stream to a map features file.
	 */
	private InputStream getMapFeatures(Properties config) {
		String file = config.getProperty("map-features");
		InputStream is;
		if (file != null) {
			try {
				log.info("reading features from file", file);
				is = new FileInputStream(file);
				return is;
			} catch (FileNotFoundException e) {
				System.err.println("Could not open " + file);
				System.err.println("Using the default map features file");
			}
		}

		is = ClassLoader.getSystemResourceAsStream(FEATURE_LIST_NAME);
		if (is == null) {
			// Try the old name, this will be removed at some point.
			is = ClassLoader.getSystemResourceAsStream(OLD_FEATURE_LIST_NAME);
			if (is == null)
				throw new ExitException("Could not find feature list resource");
			System.err.println("Warning: using old feature list file");
		}
		return is;
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {

		GarminType foundType = null;
		String foundKey = null;
		for (String tagKey : way) {
			// See if this is a line feature
			GarminType gt = lineFeatures.get(tagKey);
			if (gt != null && (foundType == null || gt.isBetter(foundType))) {
				foundKey = tagKey;
				foundType = gt;
			}
		}
		
		if (foundType != null) {
			addLine(way, foundKey, foundType);
			return;
		}

		// OK if we get here, it might be a polygon instead. Its not really
		// possible to say without checking.
		for (String tagKey : way) {
			GarminType gt = shapeFeatures.get(tagKey);
			if (gt != null) {
				addShape(way, gt);
				return;
			}
		}

		// If we get here we don't know what it is, this is pretty normal though
		if (log.isDebugEnabled())
			log.warn("no feature mapping for ", way);
	}

	private void addShape(Way way, GarminType gt) {
		// Add to the map
		List<List<Coord>> pointLists =  way.getPoints();
		for (List<Coord> points : pointLists) {
			MapShape shape = new MapShape();
			shape.setName(way.getName());
			shape.setPoints(points);
			shape.setType(gt.getType());
			shape.setMinResolution(gt.getMinResolution());

			mapper.addShape(shape);
		}
	}

	private void addLine(Way way, String tagKey, GarminType gt) {
		// Found it! Now add to the map.
		List<List<Coord>> pointLists = way.getPoints();
		for (List<Coord> points : pointLists) {
			if (points.isEmpty())
				continue;

			MapLine line = new MapLine();
			line.setName(way.getName());
			line.setPoints(points);
			line.setType(gt.getType());
			line.setMinResolution(gt.getMinResolution());

			if (way.isBoolTag("oneway"))
				line.setDirection(true);

			if (tagKey.equals("contour|elevation")) {
				line.setName(way.getTag("ele"));
			}
			mapper.addLine(line);
		}
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		for (String tagKey : node) {
			GarminType gt = pointFeatures.get(tagKey);

			if (gt != null) {
				// Add to the map
				MapPoint point = new MapPoint();
				point.setName(node.getName());
				point.setLocation(node.getLocation());
				point.setType(gt.getType());
				point.setSubType(gt.getSubtype());
				point.setMinResolution(gt.getMinResolution());

				mapper.addPoint(point);
				return;
			}
		}
	}

	/**
	 * Read the features from the file.
	 *
	 * @param in The open file.
	 * @throws IOException On any problems reading.
	 */
	private void readFeatures(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			if (line.trim().startsWith("#"))
				continue;
			
			String[] fields = line.split("\\|", -1);
			if (fields.length < N_MIN_FIELDS)
				continue;

			String type = fields[F_FEATURE_TYPE];
			log.debug("feature kind " + type);
			if (type.equals("point")) {
				log.debug("point type found");
				saveFeature(fields, pointFeatures);

			} else if (type.equals("polyline")) {
				log.debug("polyline type found");
				// Lines only have types and not subtypes on
				// the garmin side
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(fields, lineFeatures);

			} else if (type.equals("polygon")) {
				log.debug("polygon type found");
				assert fields[F_GARMIN_SUBTYPE].length() == 0;
				saveFeature(fields, shapeFeatures);

			} else {
				// Unknown type
				log.warn("unknown feature type " + type);
			}
		}
	}

	/**
	 * Create a description from the fields and put it into the features map.
	 *
	 * @param fields The fields from the map-features file.
	 * @param features This is where the GarminType is put.
	 */
	private void saveFeature(String[] fields, Map<String, GarminType> features) {
		String osm = makeKey(fields[F_OSM_TYPE], fields[F_OSM_SUBTYPE]);

		GarminType gtype;
		String gsubtype = fields[F_GARMIN_SUBTYPE];
		log.debug("subtype", gsubtype);
		if (gsubtype == null || gsubtype.length() == 0) {
			log.debug("took the subtype road");
			gtype = new GarminType(fields[F_GARMIN_TYPE]);
		} else {
			gtype = new GarminType(fields[F_GARMIN_TYPE], gsubtype);
		}

		if (fields.length > F_MIN_RESOLUTION) {
			String field = fields[F_MIN_RESOLUTION];
			int res = DEFAULT_RESOLUTION;
			if (field != null && field.length() > 0) {
				res = Integer.valueOf(field);
				if (res < 0 || res > 24) {
					System.err.println("Warning: map feature resolution out of range");
					res = 24;
				}
			}
			gtype.setMinResolution(res);
		} else {
			int res = getDefaultResolution(gtype.getType());
			gtype.setMinResolution(res);
		}
		features.put(osm, gtype);
	}

	/**
	 * Get a default resolution based on the type only.  This is historical.
	 * @param type The garmin type field.
	 * @return The minimum resolution at which the feature will be displayed.
	 */
	private int getDefaultResolution(int type) {
		// The old way - there is a built in list of min resolutions based on
		// the element type, this will eventually go.  You can't distinguish
		// between points and lines here either.
		int res;
		switch (type) {
		case 1:
		case 2:
			res = 10;
			break;
		case 3:
			res = 18;
			break;
		case 4:
			res = 19;
			break;
		case 5:
			res = 21;
			break;
		case 6:
			res = 24;
			break;
		case 0x14:
		case 0x17:
			res = 20;
			break;
		case 0x15: // coast, make always visible
			res = 10;
			break;
		default:
			res = 24;
			break;
		}

		return res;
	}

	private String makeKey(String key, String val) {
		return key + '|' + val;
	}

	private static class GarminType {
		private static int nextIndex;

		private final int index;
		private final int type;
		private final int subtype;
		private int minResolution;

		GarminType(String type) {
			int it;
			try {
				it = Integer.decode(type);
			} catch (NumberFormatException e) {
				log.error("not numeric " + type);
				it = 0;
			}
			this.type = it;
			this.subtype = 0;
			this.index = getNextIndex();
		}

		GarminType(String type, String subtype) {
			int it;
			int ist;
			try {
				it = Integer.decode(type);
				ist = Integer.decode(subtype);
			} catch (NumberFormatException e) {
				log.error("not numeric " + type + ' ' + subtype);
				it = 0;
				ist = 0;
			}
			this.type = it;
			this.subtype = ist;
			this.index = getNextIndex();
		}

		public int getType() {
			return type;
		}

		public int getSubtype() {
			return subtype;
		}

		public int getMinResolution() {
			return minResolution;
		}

		public void setMinResolution(int minResolution) {
			this.minResolution = minResolution;
		}

		public static int getNextIndex() {
			return nextIndex++;
		}

		public boolean isBetter(GarminType other) {
			return index < other.getIndex();
		}

		public int getIndex() {
			return index;
		}
	}
}
