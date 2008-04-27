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
import java.util.List;
import java.util.Properties;
import java.util.Map;

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

	// For reading the file
	private final MapFeatureReader mapFeatureReader = new MapFeatureReader();

	// Collector for saving the created features.
	private final MapCollector mapper;

	private final Map<String, Type> shapeFeatures;
	private final Map<String, Type> lineFeatures;
	private final Map<String, Type> pointFeatures;

	FeatureListConverter(MapCollector collector, Properties config) {
		this.mapper = collector;

		InputStream is = getMapFeaturesInputStream(config);

		try {
			Reader r = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(r);

			mapFeatureReader.readFeatures(br);

		} catch (UnsupportedEncodingException e) {
			log.error("reading features failed");
		} catch (IOException e) {
			log.error("reading features failed");
		}

		shapeFeatures = mapFeatureReader.getShapeFeatures();
		lineFeatures = mapFeatureReader.getLineFeatures();
		pointFeatures = mapFeatureReader.getPointFeatures();
	}

	/**
	 * Constructor used for new style system.  To begin with we are just
	 * making use of the old way of doing the styles.
	 * @param mapper The map collector.
	 * @param input The map-features that is built into the style.
	 * @throws IOException If reading the file fails.
	 */
	FeatureListConverter(MapCollector mapper, Reader input) throws IOException {
		this.mapper = mapper;

		mapFeatureReader.readFeatures(new BufferedReader(input));

		shapeFeatures = mapFeatureReader.getShapeFeatures();
		lineFeatures = mapFeatureReader.getLineFeatures();
		pointFeatures = mapFeatureReader.getPointFeatures();
	}

	/**
	 * Get an input stream to a map-features file.  This could have been on the
	 * command line, in which case return an open handle to it.
	 *
	 * @param config Properties that may contain the name of a file to use.
	 * @return An open stream to a map features file.
	 */
	private InputStream getMapFeaturesInputStream(Properties config) {
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
	 * <p>
	 * Note that there is no way to know if something is a polygon or a line
	 * feature.  You just have to look them up.  A way can be taged as both
	 * a line and a shape.  In this case we prefer the shape.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		Type foundType = null;

		// Try looking for polygons first.
		for (String tagKey : way) {
			Type gt = shapeFeatures.get(tagKey);
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
			Type gt = lineFeatures.get(tagKey);
			if (gt != null && (foundType == null || gt.isBetter(foundType))) {
				foundKey = tagKey;
				foundType = gt;
			}
		}
		
		if (foundType != null) {
			addLine(way, foundKey, foundType);
		}
	}

	private void addShape(Way way, Type gt) {
		// Add to the map
		List<Coord> points =  way.getPoints();
		MapShape shape = new MapShape();
		shape.setName(way.getName());
		shape.setPoints(points);
		shape.setType(gt.getType());
		shape.setMinResolution(gt.getMinResolution());

		mapper.addShape(shape);
	}

	private void addLine(Way way, String tagKey, Type gt) {
		// Found it! Now add to the map.
		List<Coord> points = way.getPoints();
		if (points.isEmpty())
			return;

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

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {
		for (String tagKey : node) {
			Type gt = pointFeatures.get(tagKey);

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
}
