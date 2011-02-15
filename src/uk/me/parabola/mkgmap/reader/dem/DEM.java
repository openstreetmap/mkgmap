/*
 * Copyright (C) 2009 Christian Gawron
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
 * Author: Christian Gawron
 * Create date: 03-Jul-2009
 */
package uk.me.parabola.mkgmap.reader.dem;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.util.EnhancedProperties;


/**
 * Create contour lines using an algorithm similar to that described in <a
 * href="http://mapcontext.com/autocarto/proceedings/auto-carto-5/pdf/an-adaptive-grid-contouring-algorithm.pdf">An
 * Adaptive Grid Contouring Algorithm</a> by Downing and Zoraster.
 */
public abstract class DEM {
	private static final Logger log = Logger.getLogger(DEM.class);

	private final static double epsilon = 1e-9;
	final protected static double delta = 1.5;
	private final static int maxPoints = 200000;
	private final static double minDist = 15;
	private final static double maxDist = 21;

	protected static int M = 1200;
	protected static int N = M;
	protected static double res = 1.0 / N;
	private static int id = -1;

	protected int lat;
	protected int lon;

	protected abstract double ele(int x, int y);

	protected abstract void read(int minLon, int minLat, int maxLon, int maxLat);

	public static void createContours(LoadableMapDataSource mapData, EnhancedProperties config) {
		Area bounds = mapData.getBounds();

		double minLat = Utils.toDegrees(bounds.getMinLat());
		double minLon = Utils.toDegrees(bounds.getMinLong());
		double maxLat = Utils.toDegrees(bounds.getMaxLat());
		double maxLon = Utils.toDegrees(bounds.getMaxLong());

		System.out.printf("bounds: %f %f %f %f\n", minLat, minLon, maxLat, maxLon);
		DEM data;
		String demType = config.getProperty("dem-type", "SRTM");

		try {
			String dataPath;
			Class demClass;
			if (demType.equals("ASTER")) {
				dataPath = config.getProperty("dem-path", "ASTER");
				demClass = Class.forName("uk.me.parabola.mkgmap.reader.dem.optional.GeoTiffDEM$ASTER");
			} else if (demType.equals("CGIAR")) {
				dataPath = config.getProperty("dem-path", "CGIAR");
				demClass = Class.forName("uk.me.parabola.mkgmap.reader.dem.optional.GeoTiffDEM$CGIAR");
			} else {
				dataPath = config.getProperty("dem-path", "SRTM");
				demClass = Class.forName("uk.me.parabola.mkgmap.reader.dem.HGTDEM");
			}
			Constructor<DEM> constructor = demClass.getConstructor(String.class,
					Double.TYPE, Double.TYPE,
					Double.TYPE, Double.TYPE);
			data = constructor.newInstance(dataPath, minLat, minLon, maxLat, maxLon);
		}
		catch (Exception ex) {
			throw new ExitException("failed to create DEM", ex);
		}

		Isolines lines = data.new Isolines(data, minLat, minLon, maxLat, maxLon);
		int increment = config.getProperty("dem-increment", 10);

		double minHeight = lines.getMinHeight();
		double maxHeight = lines.getMaxHeight();
		int maxLevels = config.getProperty("dem-maxlevels", 100);
		while ((maxHeight - minHeight) / increment > maxLevels)
			increment *= 2;

		String loc = config.getProperty("style-file");
		if (loc == null)
			loc = config.getProperty("map-features");
		String name = config.getProperty("style");

		if (loc == null && name == null)
			name = "default";

		LoadableMapDataSource dest = mapData;
		if (config.getProperty("dem-separate-img", false)) {
			dest = new DEMMapDataSource(mapData, config);
		}


		OsmConverter converter;
		try {
			Style style = new StyleImpl(loc, name);
			style.applyOptionOverride(config);

			converter = new StyledConverter(style, ((MapperBasedMapDataSource) dest).getMapper(), config);
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
			throw new ExitException("Could not open style " + name);
		} catch (FileNotFoundException e) {
			String name1 = (name != null) ? name : loc;
			throw new ExitException("Could not open style " + name1);
		}

		for (int level = 0; level < maxHeight; level += increment) {
			if (level < minHeight) continue;

			// create isolines
			lines.addLevel(level);

			for (Isolines.Isoline line : lines.isolines) {
				Way way = new Way(id--, line.points);
				way.addTag("contour", "elevation");
				way.addTag("ele", String.format("%d", (int) line.level));
				converter.convertWay(way);
			}
			lines.isolines.clear();
		}

		if (config.getProperty("dem-separate-img", false)) {
			MapBuilder builder = new MapBuilder();
			builder.config(config);

			// Get output directory
			String DEFAULT_DIR = ".";
			String fileOutputDir = config.getProperty("output-dir", DEFAULT_DIR);
			
			// Test if directory exists
			File outputDir = new File(fileOutputDir);
			if (!outputDir.exists()) {
				System.out.println("Output directory not found. Creating directory '" + fileOutputDir + "'");
				if (!outputDir.mkdirs()) {
					System.err.println("Unable to create output directory! Using default directory instead");
					fileOutputDir = DEFAULT_DIR;
				}
			} else if (!outputDir.isDirectory()) {
				System.err.println("The --output-dir parameter must specify a directory. The parameter is being ignored, writing to default directory instead.");
				fileOutputDir = DEFAULT_DIR;
			}		
			
			FileSystemParam params = new FileSystemParam();
			params.setMapDescription("contour lines");
			long mapName = Integer.valueOf(config.getProperty("mapname", "63240000"));
			try {
				String mapname = String.format("%08d", mapName + 10000000);
				Map map = Map.createMap(mapname, fileOutputDir, params, mapname, Sort.defaultSort());
				builder.makeMap(map, dest);
				map.close();
			}
			catch (Exception ex) {
				throw new ExitException("could not open " + mapName, ex);
			}
		}
	}

	private int lastXi = -1;
	private int lastYi = -1;

	private final static int[][] bcInv = {
			{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			{0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0},
			{-3, 0, 0, 3, 0, 0, 0, 0, -2, 0, 0, -1, 0, 0, 0, 0},
			{2, 0, 0, -2, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0},
			{0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
			{0, 0, 0, 0, -3, 0, 0, 3, 0, 0, 0, 0, -2, 0, 0, -1},
			{0, 0, 0, 0, 2, 0, 0, -2, 0, 0, 0, 0, 1, 0, 0, 1},
			{-3, 3, 0, 0, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			{0, 0, 0, 0, 0, 0, 0, 0, -3, 3, 0, 0, -2, -1, 0, 0},
			{9, -9, 9, -9, 6, 3, -3, -6, 6, -6, -3, 3, 4, 2, 1, 2},
			{-6, 6, -6, 6, -4, -2, 2, 4, -3, 3, 3, -3, -2, -1, -1, -2},
			{2, -2, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			{0, 0, 0, 0, 0, 0, 0, 0, 2, -2, 0, 0, 1, 1, 0, 0},
			{-6, 6, -6, 6, -3, -3, 3, 3, -4, 4, 2, -2, -2, -2, -1, -1},
			{4, -4, 4, -4, 2, 2, -2, -2, 2, -2, -2, 2, 1, 1, 1, 1}
	};

	private static int lastId = 1000000000;
	private static double lastX;
	private static double lastY;

	private static final int[][] off0 = {{0, 0},
			{0, 0},
			{0, 1},
			{1, 1}};

	private static final int[][] off1 = {{1, 0},
			{0, 1},
			{1, 1},
			{1, 0}};

	private static final int[] brd = {1, 2, 4, 8};

	private static final int[] rev = {2, 3, 0, 1};

	private static final int[][] mov = {{0, -1},
			{-1, 0},
			{0, 1},
			{1, 0}};


	private final double[][] bc = new double[4][4];
	private final double[] bc_y = new double[4];
	private final double[] bc_y1 = new double[4];
	private final double[] bc_y2 = new double[4];
	private final double[] bc_y12 = new double[4];
	private final double[] bc_Coeff = new double[16];
	private final double[] bc_x = new double[16];

	private void recalculateCoefficients(int xi, int yi) {

		double v00 = ele(xi, yi);
		double v0p = ele(xi, yi + 1);
		double vpp = ele(xi + 1, yi + 1);
		double vp0 = ele(xi + 1, yi);

		double vm0 = ele(xi - 1, yi);
		double v0m = ele(xi, yi - 1);
		double vmp = ele(xi - 1, yi + 1);
		double vpm = ele(xi + 1, yi - 1);
		double vmm = ele(xi - 1, yi - 1);
		double vmP = ele(xi + 2, yi - 1);
		double vPm = ele(xi - 1, yi + 2);

		double vP0 = ele(xi + 2, yi);
		double v0P = ele(xi, yi + 2);
		double vPp = ele(xi + 2, yi + 1);
		double vpP = ele(xi + 1, yi + 2);
		double vPP = ele(xi + 2, yi + 2);

		bc_y[0] = v00;
		bc_y[1] = vp0;
		bc_y[2] = vpp;
		bc_y[3] = v0p;

		bc_y1[0] = (vp0 - vm0) / 2;
		bc_y1[1] = (vP0 - v00) / 2;
		bc_y1[2] = (vPp - v0p) / 2;
		bc_y1[3] = (vpp - vmp) / 2;

		bc_y2[0] = (v0p - v0m) / 2;
		bc_y2[1] = (vpp - vpm) / 2;
		bc_y2[2] = (vpP - vp0) / 2;
		bc_y2[3] = (v0P - v00) / 2;

		bc_y12[0] = (vpp - vpm - vmp + vmm) / 4;
		bc_y12[0] = (vPp - vPm - v0p + v0m) / 4;
		bc_y12[2] = (vPP - vP0 - v0P + v00) / 4;
		bc_y12[0] = (vpP - vp0 - vmP + vm0) / 4;

		int i;

		for (i = 0; i < 4; i++) {
			bc_x[i] = bc_y[i];
			bc_x[i + 4] = bc_y1[i];
			bc_x[i + 8] = bc_y2[i];
			bc_x[i + 12] = bc_y12[i];
		}

		for (i = 0; i < 16; i++) {
			double s = 0;
			for (int k = 0; k < 16; k++) s += bcInv[i][k] * bc_x[k];
			bc_Coeff[i] = s;
		}

		int l = 0;
		for (i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				bc[i][j] = bc_Coeff[l++];
	}

	protected double gradient(double lat, double lon, double[] grad) {
		grad[0] = 0;
		grad[1] = 0;

		double x = (lon - this.lon) / res;
		double y = (lat - this.lat) / res;

		int xi = (int) x;
		int yi = (int) y;

		if (lastXi != xi || lastYi != yi) {
			log.debug("new Cell for interpolation: %d %d", xi, yi);
			recalculateCoefficients(xi, yi);
			lastXi = xi;
			lastYi = yi;
		}

		double t = x - xi;
		double u = y - yi;

		if (xi < 0 || xi > N + 1 || yi < 0 || yi > N + 1)
			throw new IndexOutOfBoundsException(String.format("(%f, %f)->(%d, %d)", lat, lon, xi, yi));

		double val = 0;
		for (int i = 3; i >= 0; i--) {
			val = t * val + ((bc[i][3] * u + bc[i][2]) * u + bc[i][1]) * u + bc[i][0];
			grad[0] = u * grad[0] + (3 * bc[3][i] * t + 2 * bc[2][i]) * t + bc[1][i];
			grad[1] = t * grad[1] + (3 * bc[i][3] * t + 2 * bc[i][2]) * t + bc[i][1];
		}

		return val;
	}

	protected double elevation(double lat, double lon) {
		double x = (lon - this.lon) / res;
		double y = (lat - this.lat) / res;

		int xi = (int) x;
		int yi = (int) y;

		if (lastXi != xi || lastYi != yi) {
			log.debug("new Cell for interpolation: %d %d", xi, yi);
			recalculateCoefficients(xi, yi);
			lastXi = xi;
			lastYi = yi;
		}

		double t = x - xi;
		double u = y - yi;

		if (xi < 0 || xi > N + 1 || yi < 0 || yi > N + 1)
			throw new IndexOutOfBoundsException(String.format("(%f, %f)->(%d, %d)", lat, lon, xi, yi));

		double val = 0;
		for (int i = 3; i >= 0; i--) {
			val = t * val + ((bc[i][3] * u + bc[i][2]) * u + bc[i][1]) * u + bc[i][0];
		}

		return val;
	}

	protected double elevation(int x, int y) {
		if (x < 0 || x > N || y < 0 || y > N)
			throw new IndexOutOfBoundsException(String.format("elevation: %d %d", x, y));
		return ele(x, y);
	}


	class Isolines {
		final DEM data;
		final int minX;
		final int maxX;
		final int minY;
		final int maxY;

		double min;
		double max;

		final ArrayList<Isoline> isolines = new ArrayList<Isoline>();

		class Isoline {
			final int id;
			final ArrayList<Coord> points;
			final double level;

			private Isoline(double level) {
				this.level = level;
				id = lastId++;
				points = new ArrayList<Coord>();
			}

			private class Edge implements Brent.Function {
				final double x0;
				final double y0;
				final double x1;
				final double y1;

				Edge(double x0, double y0, double x1, double y1) {
					this.x0 = x0;
					this.y0 = y0;
					this.x1 = x1;
					this.y1 = y1;
				}

				public double eval(double d) {
					return data.elevation(x0 + d * (x1 - x0), y0 + d * (y1 - y0)) - level;
				}
			}

			private class FN implements Brent.Function {
				double x0, y0;
				double dx, dy;

				public void setParameter(double x0, double y0, double dx, double dy) {
					this.x0 = x0;
					this.y0 = y0;
					this.dx = dx;
					this.dy = dy;
				}

				public double eval(double t) {
					return data.elevation(y0 + t * dy, x0 + t * dx) - level;
				}
			}

			private final FN fn = new FN();

			final double[] grad = new double[2];
			final double[] px = new double[4];
			final double[] py = new double[4];
			final int[] edges = new int[4];

			boolean addCell(Position p, int direction) {
				log.debug("addCell: %f %d %d %d %d", level, p.ix, p.iy, p.edge, direction);

				int c = 0;
				for (int k = 0; k < 4; k++) {
					if (k == p.edge)
						continue;

					int x0 = p.ix + off0[k][0];
					int y0 = p.iy + off0[k][1];
					int x1 = p.ix + off1[k][0];
					int y1 = p.iy + off1[k][1];

					double l0 = elevation(x0, y0) - level;
					double l1 = elevation(x1, y1) - level;

					if (Math.abs(l1) < epsilon || l0 * l1 < 0) {
						edges[c] = k;

						Brent.Function f = new Edge(data.lat + y0 * DEM.res, data.lon + x0 * DEM.res, data.lat + y1 * DEM.res, data.lon + x1 * DEM.res);
						double f0 = elevation(x0, y0) - level;
						double delta;

						if (Math.abs(1) < epsilon) {
							delta = 1;
						} else if (Math.abs(f0) < epsilon)
							throw new ExitException("implementation error!");
						else
							delta = Brent.zero(f, epsilon, 1 - epsilon);

						px[c] = data.lon + (x0 + delta * (x1 - x0)) * DEM.res;
						py[c] = data.lat + (y0 + delta * (y1 - y0)) * DEM.res;
						c++;
					}
				}


				if (c == 1) {
					p.edge = edges[0];

					double px0 = p.x;
					double py0 = p.y;
					p.x = px[0];
					p.y = py[0];
					double px1 = p.x;
					double py1 = p.y;

					double xMin = data.lon + p.ix * DEM.res;
					double xMax = xMin + DEM.res;
					double yMin = data.lat + p.iy * DEM.res;
					double yMax = yMin + DEM.res;

					refineAdaptively(xMin, yMin, xMax, yMax, px0, py0, px1, py1, direction, maxDist);

					addPoint(p.x, p.y, direction);
					p.moveCell();
					return true;
				} else {
					log.debug("addCellByStepping: %d", c);
					return addCellByStepping(p, direction, c, edges, px, py);
				}
			}

			private void refineAdaptively(double xMin, double yMin, double xMax, double yMax,
					double x0, double y0, double x1, double y1,
					int direction, double maxDist)
			{
				double dist = quickDistance(x0, y0, x1, y1);
				if (dist > maxDist) {
					double dx = x1 - x0;
					double dy = y1 - y0;

					double xm = x0 + 0.5 * dx;
					double ym = y0 + 0.5 * dy;
					double n = Math.sqrt(dx * dx + dy * dy);
					fn.setParameter(xm, ym, -dy / n, dx / n);
					Brent.Function f = fn;
					double t0 = -0.05 * res;
					double t1 = 0.05 * res;
					double f0 = f.eval(t0);
					double f1 = f.eval(t1);

					int count = 0;
					while (f0 * f1 > 0 && count++ < 20) {
						if ((count & 1) > 0)
							t0 -= 0.05 * res;
						else
							t1 += 0.05 * res;
						f0 = f.eval(t0);
						f1 = f.eval(t1);
						log.debug("refine: %f %f %f %f", t0, t1, f0, f1);
					}

					if (f0 * f1 < 0) {
						double t = Brent.zero(f, t0, t1);
						xm -= t * dy;
						ym += t * dx;
					} else {
						log.debug("refine failed: %f %f %f %f", t0, t1, f0, f1);
						return;
					}

					if (xm > xMin && xm < xMax && ym > yMin && ym < yMax)
						refineAdaptively(xMin, yMin, xMax, yMax, x0, y0, xm, ym, direction, maxDist * 1.1);
					addPoint(xm, ym, direction);
					if (xm > xMin && xm < xMax && ym > yMin && ym < yMax)
						refineAdaptively(xMin, yMin, xMax, yMax, xm, ym, x1, y1, direction, maxDist * 1.1);
				}
			}

			boolean addCellByStepping(Position p, int direction, int numEdges, int[] edges, double[] px, double[] py) {
				log.debug("addCellByStepping: %f %d %d %d %d", level, p.ix, p.iy, p.edge, direction);

				int iMin = -1;
				double md = 5000;

				for (int i = 0; i < numEdges; i++) {
					gradient(p.y, p.x, grad);
					double dist = quickDistance(p.x, p.y, px[i], py[i]);
					log.debug("distance %d: %f", i, dist);

					if (dist < md && (visited[p.iy * (N + 1) + p.ix] & brd[edges[i]]) == 0) {
						md = dist;
						iMin = i;
					}
				}

				p.edge = edges[iMin];

				double px0 = p.x;
				double py0 = p.y;
				p.x = px[iMin];
				p.y = py[iMin];
				double px1 = p.x;
				double py1 = p.y;

				double xMin = data.lon + p.ix * DEM.res;
				double xMax = xMin + DEM.res;
				double yMin = data.lat + p.iy * DEM.res;
				double yMax = yMin + DEM.res;

				refineAdaptively(xMin, yMin, xMax, yMax, px0, py0, px1, py1, direction, maxDist);

				addPoint(p.x, p.y, direction);
				p.moveCell();
				return true;
			}

			private void addPoint(double x, double y, int direction) {
				double dist = quickDistance(x, y, lastX, lastY);
				log.debug("addPoint: %f %f %f", x, y, dist);

				if (dist > minDist) {
					if (direction > 0)
						points.add(0, new Coord(y, x));
					else
						points.add(points.size(), new Coord(y, x));
					lastX = x;
					lastY = y;
				}
			}

			private void close() {
				points.add(points.size(), points.get(0));
			}

		}

		public Isolines(DEM data, double minLat, double minLon, double maxLat, double maxLon) {
			System.out.printf("init: %f %f %f %f\n", minLat, minLon, maxLat, maxLon);

			this.data = data;
			this.minX = (int) ((minLon - data.lon) / res);
			this.minY = (int) ((minLat - data.lat) / res);
			this.maxX = (int) ((maxLon - data.lon) / res);
			this.maxY = (int) ((maxLat - data.lat) / res);

			init();
		}

		private void init() {
			System.out.printf("init: %d %d %d %d\n", minX, minY, maxX, maxY);
			data.read(minX - 2, minY - 2, maxX + 2, maxY + 2);
			// we need some overlap for bicubic interpolation
			max = -1000;
			min = 10000;
			for (int i = minX; i < maxX; i++)
				for (int j = minY; j < maxY; j++) {
					if (data.elevation(i, j) < min) min = data.elevation(i, j);
					if (data.elevation(i, j) > max) max = data.elevation(i, j);
				}

			log.debug("min: %f, max: %f\n", min, max);
		}

		double getMinHeight() {
			return min;
		}

		double getMaxHeight() {
			return max;
		}

		private class Edge implements Brent.Function {
			final double x0, y0, x1, y1, level;

			Edge(double x0, double y0, double x1, double y1, double level) {
				this.x0 = x0;
				this.y0 = y0;
				this.x1 = x1;
				this.y1 = y1;
				this.level = level;
			}

			public double eval(double d) {
				return data.elevation(x0 + d * (x1 - x0), y0 + d * (y1 - y0)) - level;
			}
		}

		private class Position {
			int ix, iy;
			double x, y;
			int edge;

			Position(int ix, int iy, double x, double y, int edge) {
				this.ix = ix;
				this.iy = iy;
				this.x = x;
				this.y = y;
				this.edge = edge;
			}

			Position(Position p) {
				this.ix = p.ix;
				this.iy = p.iy;
				this.x = p.x;
				this.y = p.y;
				this.edge = p.edge;
			}

			void markEdge() {
				log.debug("marking edge: %d %d %d %d", ix, iy, edge, brd[edge]);
				visited[iy * (N + 1) + ix] |= brd[edge];
			}

			void moveCell() {
				markEdge();
				ix += mov[edge][0];
				iy += mov[edge][1];
				edge = rev[edge];
				markEdge();
			}
		}

		final byte[] visited = new byte[(N + 1) * (N + 1)];

		public void addLevel(double level) {
			if (level < min || level > max)
				return;

			System.out.printf("addLevel: %f\n", level);
			Arrays.fill(visited, (byte) 0);

			for (int y = minY; y < maxY; y++) {
				for (int x = minX; x < maxX; x++) {
					byte v = 0;
					int direction;
					// Mark the borders of the cell, represented by the four points (i, j), (i+1, j), (i, j+1), (i+1, j+1),
					// which are intersected by the contour. The values are:
					// 1: top
					// 2: left
					// 4: bottom
					// 8: right
					if (data.elevation(x, y) >= level) {
						if (data.elevation(x + 1, y) < level) {
							v |= 1;
						}
						if (data.elevation(x, y + 1) < level) {
							v |= 2;
						}
						direction = 1;
					} else {
						if (data.elevation(x + 1, y) > level) {
							v |= 1;
						}
						if (data.elevation(x, y + 1) > level) {
							v |= 2;
						}
						direction = -1;
					}

					int k = -1;

					if ((v & 1) > 0 && (visited[y * (N + 1) + x] & 1) == 0) {
						k = 0;
					} else if ((v & 2) > 0 && (visited[y * (N + 1) + x] & 2) == 0) {
						k = 1;
					}

					if (k >= 0) {
						int x0 = x + off0[k][0];
						int y0 = y + off0[k][1];
						int x1 = x + off1[k][0];
						int y1 = y + off1[k][1];

						try {
							Brent.Function f = new Edge(data.lat + y0 * DEM.res, data.lon + x0 * DEM.res,
									data.lat + y1 * DEM.res, data.lon + x1 * DEM.res,
									level);
							double f0 = elevation(x0, y0) - level;
							double f1 = elevation(x1, y1) - level;
							double delta;
							if (Math.abs(f0) < epsilon) {
								delta = 0;
							} else if (Math.abs(f1) < epsilon)
								continue;
							else
								delta = Brent.zero(f, 0, 1 - epsilon);

							Position p = new Position(x, y, data.lon + (x0 + delta * (x1 - x0)) * DEM.res, data.lat + (y0 + delta * (y1 - y0)) * DEM.res, k);
							p.markEdge();
							isolines.add(traceByStepping(level, p, direction));
						}
						catch (RuntimeException ex) {
							log.debug("error: %s", ex.toString());
							ex.printStackTrace();
							return;
						}
					}
				}
			}
		}

		private Isoline traceByStepping(double level, Position p, int direction) {
			log.debug("traceByStepping: starting contour %f %d %d %f %f %d", level, p.ix, p.iy, p.x, p.y, p.edge);
			int n = 0;
			Position startP = new Position(p);
			boolean foundEnd = false;

			Isoline line = new Isoline(level);

			while (true) {
				log.debug("traceByStepping: %f %d %d %f %f %d", level, p.ix, p.iy, p.x, p.y, p.edge);
				visited[p.iy * (N + 1) + p.ix] |= brd[p.edge];

				if (n > 0 && p.ix == startP.ix && p.iy == startP.iy && quickDistance(p.x, p.y, startP.x, startP.y) < 5) {
					log.debug("closed curve!");
					line.close();
					break;
				} else if (p.ix < minX || p.iy < minY || p.ix >= maxX || p.iy >= maxY) {
					if (foundEnd) // did we already reach one end?
					{
						log.debug("second border reached!");
						break;
					} else {
						log.debug("border reached!");
						foundEnd = true;
						n = 0;
						direction *= -1;
						p = new Position(startP);
						p.moveCell();
						continue;
					}
				}
				n++;
				if (!line.addCell(p, direction) || line.points.size() > maxPoints) {
					log.debug("ending contour");
					isolines.add(line);
					return line;
				}
			}

			return line;
		}

	}

	private static double quickDistance(double long1, double lat1, double long2, double lat2) {
		double latDiff;
		if (lat1 < lat2)
			latDiff = lat2 - lat1;
		else
			latDiff = lat1 - lat2;
		if (latDiff > 90)
			latDiff -= 180;

		double longDiff;
		if (long1 < long2)
			longDiff = long2 - long1;
		else
			longDiff = long1 - long2;
		if (longDiff > 180)
			longDiff -= 360;

		// scale longDiff by cosine of average latitude
		longDiff *= Math.cos(Math.PI / 180 * Math.abs((lat1 + lat2) / 2));

		double distDegSq = (latDiff * latDiff) + (longDiff * longDiff);

		return 40075000 * Math.sqrt(distDegSq) / 360;
	}


	private static class DEMMapDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource {
		final LoadableMapDataSource parent;
		final List<String> copyright = new ArrayList<String>();

		DEMMapDataSource(LoadableMapDataSource parent, EnhancedProperties props) {
			this.parent = parent;
			config(props);
		}

		public boolean isFileSupported(String name) {
			return false;
		}

		public void load(String name)
				throws FileNotFoundException, FormatException
		{
			throw new FormatException("load not supported");
		}

		public LevelInfo[] mapLevels() {
			return parent.mapLevels();
		}

		public String[] copyrightMessages() {
			return copyright.toArray(new String[1]);
		}
	}
}
