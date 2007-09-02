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
package uk.me.parabola.mkgmap.reader.polish;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.LevelInfo;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;


/**
 * Read an data file in Polish format.  This is the format used by a number
 * of other garmin map making programs notably cGPSmapper.
 *
 * For now we convert a bare minimum of the format and ignore its hints about
 * how the map levels should be arranged.
 *
 * @author Steve Ratcliffe
 */
public class PolishMapDataSource implements LoadableMapDataSource {
	private static final Logger log = Logger.getLogger(PolishMapDataSource.class);

	private static final int S_IMG_ID = 1;
	private static final int S_POINT = 2;
	private static final int S_POLYLINE = 3;
	private static final int S_POLYGON = 4;

	private final MapDetails mapper = new MapDetails();

	private MapPoint point;
	private MapLine polyline;
	private MapShape shape;

	private String copyright;
	private int section;

	public boolean fileSupported(String name) {
		// Supported if the extension is .mp
		if (name.endsWith(".mp"))
			return true;

		return false;
	}

	/**
	 * Load the .osm file and produce the intermediate format.
	 *
	 * @param name The filename to read.
	 * @throws FileNotFoundException If the file does not exist.
	 */
	public void load(String name) throws FileNotFoundException, FormatException {
		BufferedReader in = new BufferedReader(new FileReader(name));
		String line;
		try {
			while ((line = in.readLine()) != null) {
				if (line.trim().length() == 0 || line.charAt(0) == ';')
					continue;
				if (line.startsWith("[END"))
					endSection();
				else if (line.charAt(0) == '[')
					sectionStart(line);
				else
					processLine(line);
			}
		} catch (IOException e) {
			throw new FormatException("Reading file failed", e);
		}
	}

	public LevelInfo[] mapLevels() {
		// In the future will use the information in the file - for now it
		// is fixed.
		LevelInfo[] levels = new LevelInfo[] {
			new LevelInfo(5, 16),
			new LevelInfo(4, 18),
			new LevelInfo(3, 19),
			new LevelInfo(2, 21),
			new LevelInfo(1, 22),
			new LevelInfo(0, 24),
		};

		return levels;
	}

	/**
	 * Get the copyright message.  We use whatever was specified inside the
	 * MPF itself.
	 *
	 * @return A string description of the copyright.
	 */
	public String copyrightMessage() {
		return copyright;
	}

	/**
	 * Get the area that this map covers. Delegates to the map collector.
	 *
	 * @return The area the map covers.
	 */
	public Area getBounds() {
		return mapper.getBounds();
	}

	/**
	 * Get the list of lines that need to be rendered to the map. Delegates to
	 * the map collector.
	 *
	 * @return A list of {@link MapLine} objects.
	 */
	public List<MapLine> getLines() {
		return mapper.getLines();
	}

	public List<MapShape> getShapes() {
		return mapper.getShapes();
	}

	/**
	 * Get a list of every feature that is used in the map.  As features are
	 * created a list is kept of each separate feature that is used.  This
	 * goes into the .img file and is important for points and polygons although
	 * it doesn't seem to matter if lines are represented or not on my Legend Cx
	 * anyway.
	 *
	 * @return A list of all the types of point, polygon and polyline that are
	 * used in the map.
	 */
	public List<Overview> getOverviews() {
		return mapper.getOverviews();
	}

	public List<MapPoint> getPoints() {
		return mapper.getPoints();
	}

	/**
	 * Record that we are starting a new section.
	 * Section names are enclosed in square brackets.  Inside the sectin there
	 * are a number of lines with the key=value format.
	 *
	 * @param line The raw line from the inputfile.
	 */
	private void sectionStart(String line) {
		String name = line.substring(1, line.length() - 1);
		log.debug("section name" + name);

		if (name.equals("IMG ID")) {
			section = S_IMG_ID;
		} else if (name.equals("POI")) {
			point = new MapPoint();
			section = S_POINT;
		} else if (name.equals("POLYLINE")) {
			polyline = new MapLine();
			section = S_POLYLINE;
		} else if (name.equals("POLYGON")) {
			shape = new MapShape();
			section = S_POLYGON;
		}
	}

	/**
	 * At the end of a section, we add what ever element that we have been
	 * building to the map.
	 */
	private void endSection() {
		switch (section) {
		case S_POINT:
			mapper.addToBounds(point.getLocation());
			mapper.addPoint(point);
			break;
		case S_POLYLINE:
			if (polyline.getPoints() != null)
				mapper.addLine(polyline);
			break;
		case S_POLYGON:
			if (shape.getPoints() != null)
				mapper.addShape(shape);
			break;
		}

		// Clear the section state.
		section = 0;
	}

	/**
	 * This should be a line that is a key value pair.  We switch out to a
	 * routine that is dependant on the section that we are in.
	 *
	 * @param line The raw input line from the file.
	 */
	private void processLine(String line) {
		String[] nameVal = line.split("=", 2);
		if (nameVal.length != 2) {
			log.warn("short line? " + line);
			return;
		}
		String name = nameVal[0];
		String value = nameVal[1];

		log.debug("LINE: ", name, "|", value);
		
		switch (section) {
		case S_IMG_ID:
			imgId(name, value);
			break;
		case S_POINT:
			point(name, value);
			break;
		case S_POLYLINE:
			line(name, value);
			break;
		case S_POLYGON:
			shape(name, value);
			break;
		default:
			log.debug("line ignored");
			break;
		}
	}


	/**
	 * This is called for every line within the POI section.  The lines are
	 * key value pairs that have already been decoded into name and value.
	 * For each name we recognise we set the appropriate property on
	 * the <i>point</i>.
	 *
	 * @param name Parameter name.
	 * @param value Its value.
	 */
	private void point(String name, String value) {
		if (name.equals("Type")) {
			Integer type = Integer.decode(value);
			point.setType((type >> 8) & 0xff);
			point.setSubType(type & 0xff);
		} else if (name.equals("Label")) {
			point.setName(value);
		} else if (name.startsWith("Data")) {
			Coord co = makeCoord(value);
			point.setLocation(co);
		}
	}

	/**
	 * Called for each command in a POLYLINE section.  There will be a Data
	 * line consisting of a number of co-ordinates that must be separated out
	 * into points.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 * @see #point
	 */
	private void line(String name, String value) {
		if (name.equals("Type")) {
			polyline.setType(Integer.decode(value));
		} else if (name.equals("Label")) {
			polyline.setName(value);
		} else if (name.startsWith("Data")) {
			String[] ords = value.split("\\),\\(");
			List<Coord> points = new ArrayList<Coord>();

			for (String s : ords) {
				Coord co = makeCoord(s);
				log.debug(" L: ", co);
				mapper.addToBounds(co);
				points.add(co);
			}

			polyline.setPoints(points);
		}

	}

	/**
	 * Called for each command in a POLYGON section.  There will be a Data
	 * line consisting of a number of co-ordinates that must be separated out
	 * into points.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 * @see #line
	 */
	private void shape(String name, String value) {
		if (name.equals("Type")) {
			shape.setType(Integer.decode(value));
		} else if (name.equals("Label")) {
			shape.setName(value);
		} else if (name.startsWith("Data")) {
			String[] ords = value.split("\\),\\(");
			List<Coord> points = new ArrayList<Coord>();

			for (String s : ords) {
				Coord co = makeCoord(s);
				log.debug(" L: ", co);
				mapper.addToBounds(co);
				points.add(co);
			}

			shape.setPoints(points);
		}
	}

	/**
	 * The initial 'IMG ID' section.  Contains miscellaneous parameters for
	 * the map.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 */
	private void imgId(String name, String value) {
		if (name.equals("Copyright"))
			copyright = value;
	}

	/**
	 * Create a coordinate from a string.  The string will look similar:
	 * (2.3454,-0.23), but may not have the leading opening parenthesis.
	 * @param value A string representing a lat,long pair.
	 * @return The coordinate value.
	 */
	private Coord makeCoord(String value) {
		String[] fields = value.split("[(,)]");

		log.debug(fields, fields[0], '#', fields[1]);
		
		int i = 0;
		if (fields[0].length() == 0)
			i = 1;

		Float f1 = Float.valueOf(fields[i]);
		Double f2 = Double.valueOf(fields[i+1]);
		Coord coord = new Coord(f1, f2);
		log.debug(coord);
		return coord;
	}


}