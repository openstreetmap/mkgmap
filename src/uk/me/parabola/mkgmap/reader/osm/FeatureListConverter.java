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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.mkgmap.general.PolygonClipper;

/**
 * Reads in a CSV file from the OSMGarminMap project that contains a list
 * of OSM map features and their corresponding Garmin map feature numbers.
 *
 * @author Steve Ratcliffe
 */
class FeatureListConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(FeatureListConverter.class);

	// Specification of the fields in the map-features file.
	private static final int F_FEATURE_TYPE = 0;
	private static final int F_OSM_TYPE = 1;
	private static final int F_OSM_SUBTYPE = 2;
	private static final int F_GARMIN_TYPE = 3;
	private static final int F_GARMIN_SUBTYPE = 4;
	private static final int F_MIN_RESOLUTION = 5;

	private static final int N_MIN_FIELDS = 5;

	private static final int DEFAULT_RESOLUTION = 24;

	private static final double METERS_TO_FEET = 3.2808399;

	// Maps of osm feature names to the garmin type information
	private final Map<String, GarminType> pointFeatures = new HashMap<String, GarminType>();
	private final Map<String, GarminType> lineFeatures = new HashMap<String, GarminType>();
	private final Map<String, GarminType> shapeFeatures = new HashMap<String, GarminType>();

	// Collector for saving the created features.
	private final MapCollector mapper;

	private Area bbox;
	
	/**
	 * Constructor used for new style system.  To begin with we are just
	 * making use of the old way of doing the styles.
	 * @param mapper The map collector.
	 * @param input The map-features that is built into the style.
	 * @throws IOException If reading the file fails.
	 */
	FeatureListConverter(MapCollector mapper, Reader input) throws IOException {
		this.mapper = mapper;
		readFeatures(new BufferedReader(input));
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 * <p>
	 * Note that there is no way to know if something is a polygon or a line
	 * feature.  You just have to look them up.  A way can be taged as both
	 * a line and a shape.  In this case we prefer the shape.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		GarminType foundType = null;

		// Try looking for polygons first.
		for (String tagKey : way) {
			GarminType gt = shapeFeatures.get(tagKey);
			if (gt != null && (foundType == null || gt.isBetter(foundType))) {
				foundType = gt;
			}
		}

		if (foundType != null) {
			addShape(way, foundType);
			return;
		}

		String foundKey = null;
		for (String tagKey : way) {
			// See if this is a line feature
			GarminType gt = lineFeatures.get(tagKey);
			if (gt != null && (foundType == null || gt.isBetter(foundType))) {
				foundKey = tagKey;
				foundType = gt;
			}
		}
		
		if (foundType != null)
			clipAndAddLine(way, foundKey, foundType);

	}

	private void addShape(Way way, GarminType gt) {
		// Add to the map
		List<Coord> points =  way.getPoints();
		if (points.isEmpty())
			return;
		
		MapShape shape = new MapShape();
		shape.setName(way.getName());
		shape.setPoints(points);
		shape.setType(gt.getType());
		shape.setMinResolution(gt.getMinResolution());

		List<List<Coord>> list = PolygonClipper.clip(bbox, points);
		if (list == null)
			mapper.addShape(shape);
		else {
			for (List<Coord> lco : list) {
				MapShape nshape = new MapShape(shape);
				nshape.setPoints(lco);
				mapper.addShape(nshape);
			}
		}
	}

	private void clipAndAddLine(Way way, String tagKey, GarminType gt) {
		// Check for degenerate line
		List<Coord> points = way.getPoints();
		if (points.size() < 2)
			return;

		MapLine line = new MapLine();
		line.setName(way.getName());
		line.setPoints(points);
		line.setType(gt.getType());
		line.setMinResolution(gt.getMinResolution());

		List<List<Coord>> list = LineClipper.clip(bbox, points);
		if (list == null)
			addLine(way, tagKey, line);
		else {
			for (List<Coord> lco : list) {
				MapLine nline = new MapLine(line);
				nline.setPoints(lco);
				addLine(way, tagKey, nline);
			}
		}
	}

	private void addLine(Way way, String tagKey, MapLine line) {
		if (way.isBoolTag("oneway"))
			line.setDirection(true);

		if (tagKey.equals("contour|elevation")) {
			String ele = way.getTag("ele");
			try {
				long n = Math.round(Integer.parseInt(ele) * METERS_TO_FEET);
				line.setName(String.valueOf(n));
			} catch (NumberFormatException e) {
				line.setName(ele);
			}
		}

		mapper.addLine(line);
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		if (bbox != null && !bbox.contains(node.getLocation()))
			return;
		
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
	 * A simple implementation that looks at 'name' and 'ref', if only one
	 * of those exists, then it is used.  When they are both there then
	 * the 'ref' is in brackets after the name.
	 *
	 * <p>This is called from both nodes and ways, and I guess we should
	 * restrict the 'ref' treatment to the highways.  This is something
	 * that might happen in the styled converter.
	 *
	 * @param el The element, both nodes and ways go through here.
	 */
	public void convertName(Element el) {
		String ref = el.getTag("ref");
		String name = el.getTag("name");
		if (name == null) {
			el.setName(ref);
		} else if (ref != null) {
			StringBuffer ret = new StringBuffer(name);
			ret.append(" (");
			ret.append(ref);
			ret.append(')');
			el.setName(ret.toString());
		} else {
			el.setName(name);
		}
	}

	public void setBoundingBox(Area bbox) {
		this.bbox = bbox;
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

		String gsubtype = fields[F_GARMIN_SUBTYPE];
		log.debug("subtype", gsubtype);
		GarminType gtype;
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
