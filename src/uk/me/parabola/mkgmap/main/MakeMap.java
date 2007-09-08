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
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Overview;
import uk.me.parabola.imgfmt.app.Point;
import uk.me.parabola.imgfmt.app.PointOverview;
import uk.me.parabola.imgfmt.app.Polygon;
import uk.me.parabola.imgfmt.app.PolygonOverview;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.PolylineOverview;
import uk.me.parabola.imgfmt.app.Subdivision;
import uk.me.parabola.imgfmt.app.Zoom;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapArea;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.MapSplitter;
import uk.me.parabola.mkgmap.reader.MapReader;
import uk.me.parabola.mkgmap.reader.PropertyConfiguredReader;

import java.io.FileNotFoundException;
import static java.lang.Integer.numberOfLeadingZeros;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main routine for the command line map-making utility.
 *
 * @author Steve Ratcliffe
 */
public class MakeMap {
	private static final Logger log = Logger.getLogger(MakeMap.class);

	private static final int CLEAR_TOP_BITS = (32-15);

	private CommandArgs commandArgs;

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: mkgmap <file.osm>");
			System.exit(1);
		}

		try {
			CommandArgs a = new CommandArgs();
			a.readArgs(args);

			MakeMap mm = new MakeMap();
			mm.makeMap(a);
		} catch (ExitException e) {
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Make the map using the supplied arguments.
	 *
	 * @param args The arguments that were passed on the command line.
	 */
	private void makeMap(CommandArgs args) {
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(args.getBlockSize());
		params.setMapDescription(args.getDescription());

		commandArgs = args;

		Map map = null;
		try {
			map = Map.createMap(args.getMapname(), params);
			setOptions(map, args);

			LoadableMapDataSource src = loadFromFile(args.getFileName());
			List<Overview> features = src.getOverviews();
			processOverviews(map, features);

			processInfo(map, src);

			makeMapAreas(map, src);

		} catch (FileExistsException e) {
			throw new ExitException("File exists already", e);
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not create or write to file", e);
		} finally {
			log.info("finished making map, closing");
			if (map != null)
				map.close();
		}
	}


	private void makeMapAreas(Map map, LoadableMapDataSource src) {
		// The top level has to cover the whole map without subdividing, so
		// do a special check to make sure.
		LevelInfo[] levels = src.mapLevels();
		LevelInfo levelInfo = levels[0];
		int maxBits = getMaxBits(src);
		if (levelInfo.getBits() < maxBits)
			maxBits = levelInfo.getBits() + 1;

		// Create the empty top level
		Zoom zoom = map.createZoom(levelInfo.getLevel()+1, maxBits);
		Subdivision topdiv = makeTopArea(src, map, zoom);

		class SourceSubdiv {
			private MapDataSource source;
			private Subdivision subdiv;

			SourceSubdiv(MapDataSource ds, Subdivision subdiv) {
				this.source = ds;
				this.subdiv = subdiv;
			}

			public MapDataSource getSource() {
				return source;
			}

			public Subdivision getSubdiv() {
				return subdiv;
			}
		}

		// We start with one map data source.
		List<SourceSubdiv> srcList = Collections.singletonList(new SourceSubdiv(src, topdiv));

		// Now the levels filled with features.
		for (LevelInfo linfo : levels) {
			List<SourceSubdiv> nextList = new ArrayList<SourceSubdiv>();

			zoom = map.createZoom(linfo.getLevel(), linfo.getBits());
			
			for (SourceSubdiv smap : srcList) {

				MapSplitter splitter = new MapSplitter(smap.getSource(), zoom);
				MapArea[] areas = splitter.split();

				for (MapArea area : areas) {
					Subdivision parent = smap.getSubdiv();
					Subdivision div = makeSubdivision(map, parent, area, zoom);
					log.debug("ADD parent-subdiv", parent, smap.getSource(), ", z=", zoom, " new=", div);
					nextList.add(new SourceSubdiv(area, div));
				}
			}

			srcList = nextList;
		}
	}

	/**
	 * Create the top level subdivision.
	 *
	 * There must be an empty zoom level at the least detailed level. As it
	 * covers the whole area in one it must be zoomed out enough so that
	 * this can be done.
     *
	 * Note that the width is a 16 bit quantity, but the top bit is a
	 * flag and so that leaves only 15 bits into which the actual width
	 * can fit.
	 *
	 * @param src The source of map data.
	 * @param map The map being created.
	 * @param zoom The zoom level.
	 * @return The new top level subdivision.
	 */
	private Subdivision makeTopArea(MapDataSource src, Map map, Zoom zoom) {
		Subdivision topdiv = map.topLevelSubdivision(src.getBounds(), zoom);
		return topdiv;
	}

	private int getMaxBits(MapDataSource src) {
		int topshift = numberOfLeadingZeros(src.getBounds().getMaxDimention());
		int minShift = Math.max(CLEAR_TOP_BITS - topshift, 0);
		return 24 - minShift;
	}

	private Subdivision makeSubdivision(Map map, Subdivision parent, MapArea ma, Zoom z) {
		List<MapPoint> points = ma.getPoints();
		List<MapLine> lines = ma.getLines();
		List<MapShape> shapes = ma.getShapes();

		Subdivision div = map.createSubdivision(parent, ma.getFullBounds(), z);

		// TODO: needs to be aware of active numbers
		if (!points.isEmpty())
			div.setHasPoints(true);
		if (!lines.isEmpty())
			div.setHasPolylines(true);
		if (!shapes.isEmpty())
			div.setHasPolygons(true);

		div.startDivision();

		processPoints(map, div, points);
		processLines(map, div, lines);
		processShapes(map, div, shapes);

		return div;
	}

	/**
	 * Set options from the command line.
	 *
	 * @param map The map to modify.
	 * @param args The command line arguments.
	 */
	private void setOptions(Map map, CommandArgs args) {
		String s = args.getCharset();
		if (s != null)
			map.setLabelCharset(s);

		int i = args.getCodePage();
		if (i != 0)
			map.setLabelCodePage(i);
	}

	/**
	 * Create the overview sections.
	 *
	 * @param map The map details.
	 * @param features The list of overview records. 
	 */
	private void processOverviews(Map map, List<Overview> features) {
		for (Overview ov : features) {
			switch (ov.getKind()) {
			case Overview.POINT_KIND:
				map.addPointOverview((PointOverview) ov);
				break;
			case Overview.LINE_KIND:
				map.addPolylineOverview((PolylineOverview) ov);
				break;
			case Overview.SHAPE_KIND:
				map.addPolygonOverview((PolygonOverview) ov);
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Set all the information that appears in the header.
	 *
	 * @param map The map to write to.
	 * @param src The source of map information.
	 */
	private void processInfo(Map map, LoadableMapDataSource src) {
		// The bounds of the map.
		map.setBounds(src.getBounds());

		// Make a few settings
		map.setPoiDisplayFlags(0);

		// You can add any old junk here.
		// But there has to be something, otherwise the map does not show up.
		map.addInfo("OSM Street map");
		map.addInfo("Program released under the GPL");
		map.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");
		map.addInfo("http://creativecommons.org/licenses/by-sa/2.0/");

		// There has to be (at least) two copyright messages or else the map
		// does not show up.  The second one will be displayed at startup,
		// although the conditions where that happens are not known.
		map.addCopyright("program licenced under GPL v2");

		// This one gets shown when you switch on, so put the actual
		// map copyright here.
		map.addCopyright(src.copyrightMessage());
	}


	private void processPoints(Map map, Subdivision div, List<MapPoint> points)
	{
		div.startPoints();
		int res = div.getResolution();

		for (MapPoint point : points) {
			if (point.getResolution() > res)
				continue;

			String name = point.getName();

			Point p = div.createPoint(name);
			p.setType(point.getType());
			p.setSubtype(point.getSubType());

			Coord coord = point.getLocation();
			p.setLatitude(coord.getLatitude());
			p.setLongitude(coord.getLongitude());

			map.addMapObject(p);
		}
	}

	private void processLines(Map map, Subdivision div,
	                          List<MapLine> lines)
	{
		div.startLines();  // Signal that we are beginning to draw the lines.
		int res = div.getResolution();
		log.info("div resolution " + res);

		int shift = div.getShift();

		for (MapLine line : lines) {
			if (line.getResolution() > res)
				continue;

			String name = line.getName();
			if (name == null)
				name = "";

			Polyline pl = div.createLine(name);
			pl.setDirection(line.isDirection());

			int lastx = 0, lasty = 0;
			List<Coord> points = line.getPoints();
			for (Coord co : points) {
				int x = co.getLongitude() >> shift;
				int y = co.getLatitude() >> shift;

				if (lastx != x || lasty != y)
					pl.addCoord(co);

				lastx = x;
				lasty = y;
			}

			pl.setType(line.getType());
			//pl.check();
			map.addMapObject(pl);
		}
	}

	private void processShapes(Map map, Subdivision div,
	                           List<MapShape> shapes)
	{
		div.startShapes();  // Signal that we are beginning to draw the shapes.
		int res = div.getResolution();

		int shift = div.getShift();

		for (MapShape shape : shapes) {
			if (shape.getResolution() > res)
				continue;

			String name = shape.getName();
			if (name == null)
				name = "";

			Polygon pg = div.createPolygon(name);

			int lastx = 0, lasty = 0;
			List<Coord> points = shape.getPoints();
			for (Coord co : points) {
				int x = co.getLongitude() >> shift;
				int y = co.getLatitude() >> shift;

				if (x != lastx || y != lasty)
					pg.addCoord(co);

				lastx = x;
				lasty = y;
			}

			pg.setType(shape.getType());
			map.addMapObject(pg);
		}
	}

	/**
	 * Load up from the file.  It is not necessary for the map reader to completely
	 * read the whole file in at once, it could pull in map-features as needed.
	 *
	 * @param name The filename or resource name to be read.
	 * @return A LoadableMapDataSource that will be used to construct the map.
	 */
	private LoadableMapDataSource loadFromFile(String name) {
		try {
			LoadableMapDataSource src;

			src = MapReader.createMapReader(name);

			// If configuration required do that now.
			if (src instanceof PropertyConfiguredReader)
				((PropertyConfiguredReader) src).config(commandArgs.getProperties());

			src.load(name);

			return src;
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open file: " + name, e);
		} catch (FormatException e) {
			throw new ExitException("Bad input file format", e);
		}
	}

}
