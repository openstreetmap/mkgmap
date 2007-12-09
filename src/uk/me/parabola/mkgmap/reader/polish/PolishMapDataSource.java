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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;


/**
 * Read an data file in Polish format.  This is the format used by a number
 * of other garmin map making programs notably cGPSmapper.
 * <p>
 * As the input format is designed for garmin maps, it is fairly easy to read
 * into mkgmap.  Not every feature of the format is read yet, but it shouldn't
 * be too difficult to add them in as needed.
 * <p>
 * Now will place elements at the level specified in the file and not at the
 * automatic level that is used in eg. the OSM reader.
 *
 * @author Steve Ratcliffe
 */
public class PolishMapDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource {
	private static final Logger log = Logger.getLogger(PolishMapDataSource.class);

	private static final int S_IMG_ID = 1;
	private static final int S_POINT = 2;
	private static final int S_POLYLINE = 3;
	private static final int S_POLYGON = 4;

	private MapPoint point;
	private MapLine polyline;
	private MapShape shape;

	private String copyright;
	private int section;
	private LevelInfo[] levels;
	private int endLevel;

	public boolean isFileSupported(String name) {
		// Supported if the extension is .mp
		if (name.endsWith(".mp") || name.endsWith(".mp.gz"))
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
		Reader reader = new InputStreamReader(openFile(name));
		BufferedReader in = new BufferedReader(reader);
		try {
			String line;
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
		if (levels == null) {
			// If it has not been set then supply some defaults.
			levels = new LevelInfo[] {
					new LevelInfo(5, 16),
					new LevelInfo(4, 18),
					new LevelInfo(3, 19),
					new LevelInfo(2, 21),
					new LevelInfo(1, 22),
					new LevelInfo(0, 24),
			};
		}
		return levels;
	}

	/**
	 * Get the copyright message.  We use whatever was specified inside the
	 * MPF itself.
	 *
	 * @return A string description of the copyright.
	 */
	public String[] copyrightMessages() {
		return new String[] {copyright};
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
		} else if (name.equals("POI") || name.equals("RGN10") || name.equals("RGN20")) {
			point = new MapPoint();
			section = S_POINT;
		} else if (name.equals("POLYLINE") || name.equals("RGN40")) {
			polyline = new MapLine();
			section = S_POLYLINE;
		} else if (name.equals("POLYGON") || name.equals("RGN80")) {
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
		default:
			log.warn("unexpected default in switch", section);
			break;
		}

		// Clear the section state.
		section = 0;
		endLevel = 0;
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
			if (!isCommonValue(point, name, value))
				point(name, value);
			break;
		case S_POLYLINE:
			if (!isCommonValue(polyline, name, value))
				line(name, value);
			break;
		case S_POLYGON:
			if (!isCommonValue(shape, name, value))
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
		}  else if (name.startsWith("Data")) {
			Coord co = makeCoord(value);
			setResolution(point, name);
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
		} else if (name.startsWith("Data")) {
			String[] ords = value.split("\\),\\(");
			List<Coord> points = new ArrayList<Coord>();

			for (String s : ords) {
				Coord co = makeCoord(s);
				if (log.isDebugEnabled())
					log.debug(" L: ", co);
				mapper.addToBounds(co);
				points.add(co);
			}

			setResolution(polyline, name);
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
		} else if (name.startsWith("Data")) {
			String[] ords = value.split("\\),\\(");
			List<Coord> points = new ArrayList<Coord>();

			for (String s : ords) {
				Coord co = makeCoord(s);
				if (log.isDebugEnabled())
					log.debug(" L: ", co);
				mapper.addToBounds(co);
				points.add(co);
			}

			shape.setPoints(points);
			setResolution(shape, name);
		}
	}

	private boolean isCommonValue(MapElement elem, String name, String value) {
		if (name.equals("Label")) {
			elem.setName(value);
		} else if (name.equals("Levels") || name.equals("EndLevel") || name.equals("LevelsNumber")) {
			try {
				endLevel = Integer.valueOf(value);
			} catch (NumberFormatException e) {
				endLevel = 0;
			}
		} else {
			return false;
		}

		// We dealt with it
		return true;
	}

	private void setResolution(MapElement elem, String name) {
		if (endLevel > 0)
			elem.setMinResolution(extractResolution(endLevel));
		else
			elem.setMinResolution(extractResolution(name));
	}

	/**
	 * Extract the resolution from the Data label.  The name will be something
	 * like Data2: from that we know it is at level 2 and we can look up
	 * the resolution.
	 *
	 * @param name The name tag DataN, where N is a digit corresponding to the
	 * level.
	 *
	 * @return The resolution that corresponds to the level.
	 */
	private int extractResolution(String name) {
		int level = Integer.valueOf(name.substring(4));
		return extractResolution(level);
	}

	/**
	 * Extract resolution from the level.
	 *
	 * @param level The level (0..)
	 * @return The resolution.
	 * @see #extractResolution(String name)
	 */
	private int extractResolution(int level) {
		int nlevels = levels.length;

		LevelInfo li = levels[nlevels - level - 1];
		return li.getBits();
	}


	/**
	 * The initial 'IMG ID' section.  Contains miscellaneous parameters for
	 * the map.
	 *
	 * @param name Command name.
	 * @param value Command value.
	 */
	private void imgId(String name, String value) {
		if (name.equals("Copyright")) {
			copyright = value;
		} else if (name.equals("Levels")) {
			int nlev = Integer.valueOf(value);
			levels = new LevelInfo[nlev];
		} else if (name.startsWith("Level")) {
			int level = Integer.valueOf(name.substring(5));
			int bits = Integer.valueOf(value);
			LevelInfo info = new LevelInfo(level, bits);

			int nlevels = levels.length;
			if (level >= nlevels)
				return;

			levels[nlevels - level - 1] = info;
		}
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
