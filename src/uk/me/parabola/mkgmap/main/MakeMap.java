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

import uk.me.parabola.mkgmap.osm.ReadOsm;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
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

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Main routine for the command line map-making utility.
 *
 * @author Steve Ratcliffe
 */
public class MakeMap {
	private static final Logger log = Logger.getLogger(MakeMap.class);

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: mkgmap <file.osm>");
			System.exit(1);
		}

		try {

			CommandArgs a = new CommandArgs();
			a.readArgs(args);
//			a.dumpOptions();

			MakeMap mm = new MakeMap();
			mm.makeMap(a);
		} catch (ExitException e) {
			System.err.println(e.getMessage());
		}
	}

	private void makeMap(CommandArgs args) {
		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription("OSM street map");

		Map map = null;
		try {
			map = Map.createMap(args.getMapname(), params);
			setOptions(map, args);

			MapDataSource src = loadFromFile(args.getFileName());

//			MapSplitter splitter = new MapSplitter(src);
//			splitter.split();
//			if (System.currentTimeMillis() > 2) {
//				return;
//			}
			
			List<Overview> features = src.getOverviews();
			processOverviews(map, features);

			processInfo(map, src);

			List<MapPoint> points = src.getPoints();
			List<MapLine> lines = src.getLines();
			List<MapShape> shapes = src.getShapes();

			Subdivision div = makeDivisions(map, src);
			if (!points.isEmpty())
				div.setHasPoints(true);
			if (!lines.isEmpty())
				div.setHasPolylines(true);
			if (!shapes.isEmpty())
				div.setHasPolygons(true);

			map.startDivision(div); // XXX should be on div?

			processPoints(map, div, points);
			processLines(map, div, lines);
			processShapes(map, div, shapes);

		} finally {
			if (map != null)
				map.close();
		}
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

		// The last one is being ignored so add a dummy one.
		map.addPolygonOverview(new PolygonOverview(0x0));

		// Let do the same for points too.
		map.addPointOverview(new PointOverview(0x0, 0x0));
	}

	/**
	 * Set all the information that appears in the header.
	 *
	 * @param map The map to write to.
	 * @param src The source of map information.
	 */
	private void processInfo(Map map, MapDataSource src) {
		// The bounds of the map.
		map.setBounds(src.getBounds());

		// Make a few settings
		map.setPoiDisplayFlags(0);

		// You can add any old junk here.
		// But there has to be something, otherwise the map does not show up.
		map.addInfo("OSM Street map");
		map.addInfo("Program released under the GPL");
		map.addInfo("Map data licenced under Creative Commons Attribution ShareAlike 2.0");

		// There has to be (at least) two copyright messages or else the map
		// does not show up.  The second one will be displayed at startup,
		// although the conditions where that happens are not known.
		map.addCopyright("program licenced under GPL v2");

		// This one gets shown when you switch on, so put the actual
		// map copyright here.
		map.addCopyright(src.copyrightMessage());
	}

	/**
	 * Make the subdivisions in the map.
	 * As we only use 1 (plus the empty top one) this will change a
	 * lot.
	 * TODO: needs to step though all zoom levels.
	 * TODO: for each zoom level, create subdivisions.
	 * TODO: return something more than a single division.
	 *
	 * @param map The map to operate on.
	 * @param src The source of map information.
	 * @return A single division.  Will be chnaged.
	 */
	private Subdivision makeDivisions(Map map, MapDataSource src) {
		Area bounds = src.getBounds();

//		bounds = new Area(lat, lng, lat + 0.05, lng + 0.05);

		// There must be an empty zoom level at the least detailed level.
		Zoom z1 = map.createZoom(1, 24);
		Subdivision topdiv = map.topLevelSubdivision(bounds, z1);

		// Create the most detailed view.
		Zoom z = map.createZoom(0, 24);
		Subdivision div = map.createSubdivision(topdiv, bounds, z);

		map.startDivision(div);
		return div;
 	}

	private void processPoints(Map map, Subdivision div, List<MapPoint> points)
	{
		div.startPoints();

		for (MapPoint point : points) {
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

		for (MapLine line : lines) {
			String name = line.getName();
			if (name == null) {
				name="";//continue;
			}

			log.debug("Road " + name + ", t=" + line.getType());
			Polyline pl = div.createLine(name);
			pl.setDirection(line.isDirection());

			List<Coord> points = line.getPoints();
			for (Coord co : points) {
				if (log.isDebugEnabled())
				log.debug("  point at", co, '/', co.getLatitude(), co.getLongitude());
				pl.addCoord(co);
			}

			pl.setType(line.getType());
			map.addMapObject(pl);
		}
	}

	private void processShapes(Map map, Subdivision div,
	                           List<MapShape> shapes)
	{
		div.startShapes();  // Signal that we are beginning to draw the shapes.

		for (MapShape shape : shapes) {
			String name = shape.getName();
			if (name == null) {
				name="";//continue;
			}

			log.debug("Shape " + name + ", t=" + shape.getType());
			Polygon pg = div.createPolygon(name);

			List<Coord> points = shape.getPoints();
			for (Coord co : points) {
				log.debug("  point at " + co);
				pg.addCoord(co);
			}

			pg.setType(shape.getType());
			map.addMapObject(pg);
		}
	}

	private MapDataSource loadFromFile(String name) {
		try {
			MapDataSource src = new ReadOsm();

			src.load(name);

			return src;
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open file: " + name, e);
		} catch (FormatException e) {
			throw new ExitException("Bad input file format", e);
		}
	}

}
