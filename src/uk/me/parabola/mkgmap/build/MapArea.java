/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: 21-Jan-2007
 */
package uk.me.parabola.mkgmap.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import uk.me.parabola.util.ShapeSplitter;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.RoadNetwork;
import uk.me.parabola.imgfmt.app.trergn.Overview;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.FilterConfig;
import uk.me.parabola.mkgmap.filters.LineSizeSplitterFilter;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
import uk.me.parabola.mkgmap.filters.MapFilter;
import uk.me.parabola.mkgmap.filters.MapFilterChain;
import uk.me.parabola.mkgmap.filters.PolygonSplitterFilter;
import uk.me.parabola.mkgmap.filters.PolygonSubdivSizeSplitterFilter;
import uk.me.parabola.mkgmap.filters.PredictFilterPoints;
import uk.me.parabola.mkgmap.general.MapDataSource;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * A sub area of the map.  We have to divide the map up into areas to meet the
 * format of the Garmin map.  This class holds all the map elements that belong
 * to a particular area and provides a way of splitting areas into smaller ones.
 *
 * It also acts as a map data source so that we can derive lower level
 * areas from it.
 *
 * @author Steve Ratcliffe
 */
public class MapArea implements MapDataSource {
	private static final Logger log = Logger.getLogger(MapArea.class);

	private static final int INITIAL_CAPACITY = 100;
	private static final int MAX_RESOLUTION = 24;
	private static final int LARGE_OBJECT_DIM = 8192;
	
	public static final int POINT_KIND    = 0;
	public static final int LINE_KIND     = 1;
	public static final int SHAPE_KIND    = 2;
	public static final int XT_POINT_KIND = 3;
	public static final int XT_LINE_KIND  = 4;
	public static final int XT_SHAPE_KIND = 5;
	public static final int NUM_KINDS     = 6;

	// This is the initial area.
	private Area bounds;

	// Because ways may extend beyond the bounds, we keep track of the actual
	// bounding box here.
	private int minLat = Integer.MAX_VALUE;
	private int minLon = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLon = Integer.MIN_VALUE;

	// The contents of the area.
	private final List<MapPoint> points = new ArrayList<>(INITIAL_CAPACITY);
	private final List<MapLine> lines = new ArrayList<>(INITIAL_CAPACITY);
	private final List<MapShape> shapes = new ArrayList<>(INITIAL_CAPACITY);

	// amount of space required for the contents
	private final int[] sizes = new int[NUM_KINDS];

	private int nActivePoints;
	private int nActiveIndPoints;
	private int nActiveLines;
	private int nActiveShapes;

	/** The resolution that this area is at */
	private int areaResolution;
	private final boolean splitPolygonsIntoArea;
	private int splittableCount;

	private Long2ObjectOpenHashMap<Coord> areasHashMap;

	/**
	 * Create a map area from the given map data source.  This map
	 * area will have the same bounds as the map data source and
	 * will contain all the same map elements.
	 *
	 * @param src The map data source to initialise this area with.
	 * @param resolution The resolution of this area.
	 * @param splitPolygonsIntoArea aligns subareas as powerOf2 and splits polygons into the subareas.
	 */
	public MapArea(MapDataSource src, int resolution, boolean splitPolygonsIntoArea) {
		this.areaResolution = 0; // don't want addSize() to gather information for this MapArea
		this.bounds = src.getBounds();
		this.splitPolygonsIntoArea = splitPolygonsIntoArea;
		for (MapPoint p : src.getPoints()) {
			if (p.getMaxResolution() < resolution)
				continue;
			if(bounds.contains(p.getLocation()))
				addPoint(p);
			else
				log.error("Point with type 0x" + Integer.toHexString(p.getType()) + " at " + p.getLocation().toOSMURL() +
					  " is outside of the map area centred on " + bounds.getCenter().toOSMURL() +
					  " width = " + bounds.getWidth() + " height = " + bounds.getHeight() + " resolution = " + areaResolution);
		}
		addLines(src, resolution);
		addPolygons(src, resolution);
		this.areaResolution = resolution;
	}

	/**
	 * Add the polygons
	 * @param src The map data.
	 * @param resolution The current resolution of the layer.
	 */
	private void addPolygons(MapDataSource src, final int resolution) {
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapShape shape = (MapShape) element;
				addShape(shape);
			}
		};

		PolygonSubdivSizeSplitterFilter filter = new PolygonSubdivSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);

		for (MapShape s : src.getShapes()) {
			if (s.getMaxResolution() < resolution)
				continue;
			if (splitPolygonsIntoArea || this.bounds.isEmpty() || this.bounds.contains(s.getBounds()))
				// if splitPolygonsIntoArea, don't want to have other splitting as well.
				// PolygonSubdivSizeSplitterFilter is a bit drastic - filters by both size and number of points
				// so use splitPolygonsIntoArea logic for this as well. This is fine as long as there
				// aren't bits of the shape outside the initial area.
				addShape(s);
			else
				filter.doFilter(s, chain);
		}
	}

	/**
	 * Add the lines, making sure that they are not too big for resolution
	 * that we are working with.
	 * @param src The map data.
	 * @param resolution The current resolution of the layer.
	 */
	private void addLines(MapDataSource src, final int resolution) {
		// Split lines for size, such that it is appropriate for the
		// resolution that it is at.
		MapFilterChain chain = new MapFilterChain() {
			public void doFilter(MapElement element) {
				MapLine line = (MapLine) element;
				addLine(line);
			}
		};

		LineSizeSplitterFilter filter = new LineSizeSplitterFilter();
		FilterConfig config = new FilterConfig();
		config.setResolution(resolution);
		config.setBounds(bounds);
		filter.init(config);
		for (MapLine l : src.getLines()) {
			if (l.getMaxResolution() < resolution)
				continue;
// %%% ??? if not appearing at this level no need to filter
			filter.doFilter(l, chain);
		}
	}

	/**
	 * Create an map area with the given initial bounds.
	 *
	 * @param area The bounds for this area.
	 * @param resolution The minimum resolution for this area.
	 * @param splitPolygonsIntoArea splits polygons into the subareas.
	 */
	private MapArea(Area area, int resolution, boolean splitPolygonsIntoArea) {
		bounds = area;
		areaResolution = resolution;
		this.splitPolygonsIntoArea = splitPolygonsIntoArea;
	}

	/**
	 * Split this area into several pieces. All the map elements are reallocated
	 * to the appropriate subarea.  Usually this instance would now be thrown
	 * away and the new sub areas used instead.
	 * <p>
	 * This code is dealing with a lot of factors that govern the splitting, eg:
	 *  splitPolygonsIntoArea,
	 *  tooSmallToDivide,
	 *  item.minResolution vs. areaResolution,
	 *  number/size of items and the limits of a subDivision,
	 *  items that exceed maximum subDivision on their own,
	 *  items that extend up to 50% outside the current area,
	 *  items bigger than this.
	 *
	 * @param nx The number of pieces in the x (longitude) direction.
	 * @param ny The number of pieces in the y direction.
	 * @param bounds the bounding box that is used to create the areas.
	 * @param tooSmallToDivide the area is small and data overflows; split into overflow areas
	 *
	 * @return An array of the new MapArea's or null if can't split.
	 */
	public MapArea[] split(int nx, int ny, Area bounds, boolean tooSmallToDivide) {
		int resolutionShift = MAX_RESOLUTION - areaResolution;
		Area[] areas = bounds.split(nx, ny, resolutionShift);
		if (areas == null) { //  Failed to split!
			if (log.isDebugEnabled()) { // see what is here
				for (MapLine e : this.lines)
					if (e.getMinResolution() <= areaResolution)
						log.debug("line. locn=", e.getPoints().get(0).toOSMURL(),
							 " type=", uk.me.parabola.mkgmap.reader.osm.GType.formatType(e.getType()),
							 " name=", e.getName(), " min=", e.getMinResolution(), " max=", e.getMaxResolution());
				for (MapShape e : this.shapes)
					if (e.getMinResolution() <= areaResolution)
						log.debug("shape. locn=", e.getPoints().get(0).toOSMURL(),
							 " type=", uk.me.parabola.mkgmap.reader.osm.GType.formatType(e.getType()),
							 " name=", e.getName(), " min=", e.getMinResolution(), " max=", e.getMaxResolution(),
							 " full=", e.getFullArea(),
							 " calc=", uk.me.parabola.mkgmap.filters.ShapeMergeFilter.calcAreaSizeTestVal(e.getPoints()));
				// the main culprits are lots of bits of sea and coastline in an overview map (res 12)
			}
			return null;
		}

		MapArea[] mapAreas = new MapArea[nx * ny];
		log.info("Splitting area " + bounds + " into " + nx + "x" + ny + " pieces at resolution " + areaResolution, tooSmallToDivide);
		List<MapArea> addedAreas = new ArrayList<>();
		for (int i = 0; i < mapAreas.length; i++) {
			mapAreas[i] = new MapArea(areas[i], areaResolution, splitPolygonsIntoArea);
			if (log.isDebugEnabled())
				log.debug("area before", mapAreas[i].getBounds());
		}

		int xbase30 = areas[0].getMinLong() << Coord.DELTA_SHIFT;
		int ybase30 = areas[0].getMinLat() << Coord.DELTA_SHIFT;
		int dx30 = areas[0].getWidth() << Coord.DELTA_SHIFT;
		int dy30 = areas[0].getHeight() << Coord.DELTA_SHIFT;

		// Some of the work done by PolygonSubdivSizeSplitterFilter now done here
		final int maxSize = Math.min((1<<24)-1, Math.max(MapSplitter.MAX_DIVISION_SIZE << (MAX_RESOLUTION - areaResolution), 0x8000));

		/**
		 * These constants control when an item (shape unless splitPolygonsIntoArea or line) is shifted into its own MapArea/SubDivision.
		 * Generally, an item is allowed into the MapArea chosen by centre provided it is no bigger than the MapArea.
		 * This means that there could be big items near the edges of the MapArea that stick out by almost half, so must
		 * ensure that this doesn't cause the mapArea to exceed subDivision size limits.
		 * When the MapArea get small, we don't want to shift lots if items into their own areas;
		 * The *2 of LARGE_OBJECT_DIM is to keep to the same behaviour as earlier versions.
		 */
		final int maxWidth = Math.max(Math.min(areas[0].getWidth(), maxSize/2), LARGE_OBJECT_DIM*2);
		final int maxHeight = Math.max(Math.min(areas[0].getHeight(), maxSize/2), LARGE_OBJECT_DIM*2);

		// Now sprinkle each map element into the correct map area.

		// do shapes first because want these to define the primary area
		// and don't have a good tooSmallToDivide strategy when not splitPolygonsIntoArea.
		if (tooSmallToDivide) {
			distShapesIntoNewAreas(addedAreas, mapAreas[0]);
		} else {
			for (MapShape e : this.shapes) {
				Area shapeBounds = e.getBounds();
				if (splitPolygonsIntoArea || shapeBounds.getMaxDimension() > maxSize) {
					splitIntoAreas(mapAreas, e);
					continue;
				}
				int areaIndex = pickArea(mapAreas, e, xbase30, ybase30, nx, ny, dx30, dy30);
				if ((shapeBounds.getHeight() > maxHeight || shapeBounds.getWidth() > maxWidth) &&
				    !areas[areaIndex].contains(shapeBounds)) {
					MapArea largeObjectArea = new MapArea(shapeBounds, areaResolution, true); // use splitIntoAreas to deal with overflow
					largeObjectArea.addShape(e);
					addedAreas.add(largeObjectArea);
					continue;
				}
				mapAreas[areaIndex].addShape(e);
			}
		}

		if (tooSmallToDivide) {
			distPointsIntoNewAreas(addedAreas, mapAreas[0]);
		} else {
			for (MapPoint p : this.points) {
				int areaIndex = pickArea(mapAreas, p, xbase30, ybase30, nx, ny, dx30, dy30);
				mapAreas[areaIndex].addPoint(p);
			}
		}

		if (tooSmallToDivide) {
			distLinesIntoNewAreas(addedAreas, mapAreas[0]);
		} else {
			for (MapLine l : this.lines) {
				// Drop any zero sized lines.
				if (l instanceof MapRoad == false && l.getRect().height <= 0 && l.getRect().width <= 0)
					continue;
				Area lineBounds = l.getBounds();
				int areaIndex = pickArea(mapAreas, l, xbase30, ybase30, nx, ny, dx30, dy30);
				if ((lineBounds.getHeight() > maxHeight || lineBounds.getWidth() > maxWidth) &&
				    !areas[areaIndex].contains(lineBounds)) {
					MapArea largeObjectArea = new MapArea(lineBounds, areaResolution, false);
					largeObjectArea.addLine(l);
					addedAreas.add(largeObjectArea);
					continue;
				}
				mapAreas[areaIndex].addLine(l);
			}
		}

		if (!addedAreas.isEmpty()) {
			// combine list and array
			int pos = mapAreas.length;
			mapAreas = Arrays.copyOf(mapAreas, mapAreas.length + addedAreas.size());
			for (MapArea ma : addedAreas) {
				if (ma.getBounds() == null) // distShapesIntoNewAreas etc didn't know how big it was going to be
					ma.setBounds(ma.getFullBounds()); // so set to bounds of all elements
				mapAreas[pos++] = ma;
			}
		}
		return mapAreas;
	}

	private void distPointsIntoNewAreas(List<MapArea> addedAreas, MapArea primaryArea) {
		MapArea extraArea = primaryArea;
		for (MapPoint p : this.points)
			if (p.getMinResolution() > areaResolution) // doesn't add to subDivision
				primaryArea.addPoint(p);
			else {
				if (!extraArea.canAddSize(p, POINT_KIND)) {
					extraArea = new MapArea((Area)null, areaResolution, false);
					addedAreas.add(extraArea);
				}
				extraArea.addPoint(p);
			}
	}


	private void distLinesIntoNewAreas(List<MapArea> addedAreas, MapArea primaryArea) {
		MapArea extraArea = primaryArea;
		for (MapLine l : this.lines)
			if (l.getMinResolution() > areaResolution) // doesn't add to subDivision
				primaryArea.addLine(l);
			else {
				if (!extraArea.canAddSize(l, LINE_KIND)) {
					extraArea = new MapArea((Area)null, areaResolution, false);
					addedAreas.add(extraArea);
				}
				extraArea.addLine(l);
			}
	}


	private void distShapesIntoNewAreas(List<MapArea> addedAreas, MapArea primaryArea) {
		MapArea extraArea = primaryArea;
		for (MapShape e : this.shapes)
			if (e.getMinResolution() > areaResolution) // doesn't add to subDivision
				primaryArea.addShape(e);
			else {
				if (!extraArea.canAddSize(e, SHAPE_KIND)) {
					extraArea = new MapArea((Area)null, areaResolution, true); // use splitIntoAreas to deal with overflow
					addedAreas.add(extraArea);
				}
				extraArea.addShape(e);
			}
	}

	/**
	 * Get the full bounds of this area.  As lines and polylines are
	 * added then may go outside of the initial area.  When this happens
	 * we need to increase the size of the area.
	 *
	 * @return The full size required to hold all the included
	 * elements.
	 */
	public Area getFullBounds() {
		return new Area(minLat, minLon, maxLat, maxLon);
	}

	/**
	 * Get an estimate of the size of the RGN space that will be required to
	 * hold the elements
	 *
	 * @return Estimates of the max size that will be needed in the RGN file
	 * for the points/lines/shapes in this sub-division.
	 */
	public int[] getEstimatedSizes() {
		return sizes;
	}

	/**
	 * Get the initial bounds of this area.  That is the initial
	 * bounds before anything was added.
	 *
	 * @return The initial bounds as when it was created.
	 * @see #getFullBounds
	 */
	public Area getBounds() {
		return bounds;
	}

	/**
	 * override the initial bounds of this area.
	 */
	public void setBounds(Area actualBounds) {
		this.bounds = actualBounds;
	}

	/**
	 * Get a list of all the points.
	 *
	 * @return The points.
	 */
	public List<MapPoint> getPoints() {
		return points;
	}

	/**
	 * Get a list of all the lines.
	 *
	 * @return The lines.
	 */
	public List<MapLine> getLines() {
		return lines;
	}

	/**
	 * Get a list of all the shapes.
	 *
	 * @return The shapes.
	 */
	public List<MapShape> getShapes() {
		return shapes;
	}

	public RoadNetwork getRoadNetwork() {
		// I don't think this is needed here.
		return null;
	}

	/**
	 * This is not used for areas.
	 * @return Always returns null.
	 */
	public List<Overview> getOverviews() {
		return null;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasPoints() {
		return nActivePoints > 0;
	}

	/**
	 * True if there are active indexed points in the area.
	 * @return True if any active indexed points in the area.
	 */
	public boolean hasIndPoints() {
		return nActiveIndPoints > 0;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasLines() {
		return nActiveLines > 0;
	}

	/**
	 * Return number of lines in this area.
	 */
	public int getNumLines() {
		return nActiveLines;
	}

	/**
	 * Return number of shapes in this area.
	 */
	public int getNumShapes() {
		return nActiveShapes;
	}

	/**
	 * Return number of points in this area.
	 */
	public int getNumPoints() {
		return nActivePoints + nActiveIndPoints;
	}

	/**
	 * True if there are any 'active' points in this area.  Ie ones that will be
	 * shown because their resolution is at least as high as that of the
	 * area.
	 *
	 * @return True if any active points in this area.
	 */
	public boolean hasShapes() {
		return nActiveShapes > 0;
	}

	/**
	 * Can this area be split (in a way that reduces subDivision content)?
	 * 
	 * If more than one item (point/line/shape) to be shown at this resolution then yes.
	 * Must use count of items rather than the sum of numElements that addSize() estimates
	 * because these are generated by later filters after the contents of the subDivision
	 * has been set.
	 *
	 * A single shape is considered splittable because it will end up in a splitPolygonsIntoArea
	 * area and this can then be divided down to <= MIN_DIMENSION(=10) pixel rectangle;
	 * so, if, somehow, the shape passed through every pixel that would be 100 points, well
	 * below MAX_POINTS_IN_ELEMENT, MAX_RNG_SIZE, MAX_XT_SHAPE_SIZE
	 */
	public boolean canSplit() {
		return splittableCount > 1;
	}

	/**
	 * Add an estimate of the size that will be required to hold this element
	 * if it should be displayed at the given resolution.  We also keep track
	 * of the number of <i>active</i> elements here ie elements that will be
	 * shown because they are at a resolution at least as great as the resolution
	 * of the area.
	 *
	 * @param el The element containing the minimum resolution that it will be
	 * displayed at.
	 * @param kind What kind of element this is KIND_POINT etc.
	 */
	private void addSize(MapElement el, int kind) {

		int res = el.getMinResolution();
		if (res > areaResolution || res > MAX_RESOLUTION)
			return;
		++splittableCount;

		int numPoints;
		int numElements;

		switch (kind) {
		case POINT_KIND:
		case XT_POINT_KIND:
			// Points are predictably less than 10 bytes.
			sizes[kind] += 9;
			if(!el.hasExtendedType()) {
				if(((MapPoint) el).isCity())
					nActiveIndPoints++;
				else
					nActivePoints++;
			}
			break;

		case LINE_KIND:
		case XT_LINE_KIND:
			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			numPoints = PredictFilterPoints.predictedMaxNumPoints(((MapLine) el).getPoints(), areaResolution,
				// assume MapBuilder.doRoads is true. subDiv.getZoom().getLevel() == 0 is maximum resolution
				((MapLine) el).isRoad() && areaResolution == MAX_RESOLUTION);
			if (numPoints <= 1 && !((MapLine) el).isRoad())
				return;
			numElements = 1 + ((numPoints - 1) / LineSplitterFilter.MAX_POINTS_IN_LINE);
			sizes[kind] += numElements * 11 + numPoints * 4; // very pessimistic, typically less than 2 bytes are needed for one point
			if (!el.hasExtendedType())
				nActiveLines += numElements;
			break;

		case SHAPE_KIND:
		case XT_SHAPE_KIND:
			++splittableCount; // see canSplit() above
			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			numPoints = PredictFilterPoints.predictedMaxNumPoints(((MapShape) el).getPoints(), areaResolution, false);
			if (numPoints <= 3)
				return;
			numElements = 1 + ((numPoints - 1) / PolygonSplitterFilter.MAX_POINT_IN_ELEMENT);
			sizes[kind] += numElements * 11 + numPoints * 4; // very pessimistic, typically less than 2 bytes are needed for one point
			if (!el.hasExtendedType())
				nActiveShapes += numElements;
			break;

		default:
			log.error("should not be here");
			assert false;
			break;
		}

	}

	/**
	 * Will element fit nicely?
	 * Limit to WANTED_MAX_AREA_SIZE which is smaller than MAX_XT_xxx_SIZE
	 * so don't need to check down to the last detail
	 *
	 * @param el The element. Assume want to display it at this resolution
	 * @param kind What kind of element this is: KIND_POINT/LINE/SHAPE.
	 */
	private boolean canAddSize(MapElement el, int kind) {

		int numPoints;
		int numElements;
		int sumSize = 0;
		for (int s : sizes)
			sumSize += s;

		switch (kind) {
		case POINT_KIND:
			if (getNumPoints() >= MapSplitter.MAX_NUM_POINTS)
				return false;
			// Points are predictably less than 10 bytes.
			if ((sumSize + 9) > MapSplitter.WANTED_MAX_AREA_SIZE)
				return false;
			break;

		case LINE_KIND:
			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			numPoints = PredictFilterPoints.predictedMaxNumPoints(((MapLine) el).getPoints(), areaResolution,
				// assume MapBuilder.doRoads is true. subDiv.getZoom().getLevel() == 0 is maximum resolution
				((MapLine) el).isRoad() && areaResolution == MAX_RESOLUTION);
			if (numPoints <= 1 && !((MapLine) el).isRoad())
				break;
			numElements = 1 + ((numPoints - 1) / LineSplitterFilter.MAX_POINTS_IN_LINE);
			if (getNumLines() + numElements > MapSplitter.MAX_NUM_LINES)
				return false;
			// very pessimistic, typically less than 2 bytes are needed for one point
			if ((sumSize + numElements * 11 + numPoints * 4) > MapSplitter.WANTED_MAX_AREA_SIZE)
				return false;
			break;

		case SHAPE_KIND:
			// Estimate the size taken by lines and shapes as a constant plus
			// a factor based on the number of points.
			numPoints = PredictFilterPoints.predictedMaxNumPoints(((MapShape) el).getPoints(), areaResolution, false);
			if (numPoints <= 3)
				break;
			numElements = 1 + ((numPoints - 1) / PolygonSplitterFilter.MAX_POINT_IN_ELEMENT);
			// very pessimistic, typically less than 2 bytes are needed for one point
			if ((sumSize + numElements * 11 + numPoints * 4) > MapSplitter.WANTED_MAX_AREA_SIZE)
				return false;
			break;

		}
		return true;
	}

	/**
	 * Add a single point to this area.
	 *
	 * @param p The point to add.
	 */
	private void addPoint(MapPoint p) {
		points.add(p);
		addToBounds(p.getLocation()); 
		addSize(p, p.hasExtendedType()? XT_POINT_KIND : POINT_KIND);
	}

	/**
	 * Add a single line to this area.
	 *
	 * @param l The line to add.
	 */
	private void addLine(MapLine l) {
		lines.add(l);
		addToBounds(l.getBounds());
		addSize(l, l.hasExtendedType()? XT_LINE_KIND : LINE_KIND);
	}

	/**
	 * Add a single shape to this map area.
	 *
	 * @param s The shape to add.
	 */
	private void addShape(MapShape s) {
		shapes.add(s);
		addToBounds(s.getBounds());
		addSize(s, s.hasExtendedType()? XT_SHAPE_KIND : SHAPE_KIND);
	}

	/**
	 * Add to the bounds of this area.  That is the new bounds
	 * for this area will cover the existing ones plus the new
	 * area.
	 *
	 * @param a Area to add into this map area.
	 */
	private void addToBounds(Area a) {
		int l = a.getMinLat();
		if (l < minLat)
			minLat = l;
		l = a.getMaxLat();
		if (l > maxLat)
			maxLat = l;

		l = a.getMinLong();
		if (l < minLon)
			minLon = l;
		l = a.getMaxLong();
		if (l > maxLon)
			maxLon = l;
	}

	/**
	 * Add to bounds considering high precision values. 
	 * @param co
	 */
	private void addToBounds(Coord co) {
		int lat30 = co.getHighPrecLat();
		int latLower  = lat30 >> Coord.DELTA_SHIFT;
		int latUpper  = (latLower << Coord.DELTA_SHIFT) < lat30 ? latLower + 1 : latLower;
		if (latLower < minLat)
			minLat = latLower;
		if (latUpper > maxLat)
			maxLat = latUpper;
		
		int lon30 = co.getHighPrecLon();
		int lonLeft = lon30 >> Coord.DELTA_SHIFT;
		int lonRight = (lonLeft << Coord.DELTA_SHIFT) < lon30 ? lonLeft + 1 : lonLeft;
		if (lonLeft < minLon)
			minLon = lonLeft;
		if (lonRight > maxLon)
			maxLon = lonRight;
	}

	
	
	/**
	 * Out of all the available areas, it picks the one that the map element
	 * should be placed into.
	 *
	 * Since we know how the area is divided (equal sizes) we can work out
	 * which one it fits into without stepping through them all and checking
	 * coordinates.
	 *
	 * @param areas The available areas to choose from.
	 * @param e The map element.
	 * @param xbase30 The 30-bit x coord at the origin
	 * @param ybase30 The 30-bit y coord of the origin
	 * @param nx number of divisions.
	 * @param ny number of divisions in y.
	 * @param dx30 The size of each division (x direction)
	 * @param dy30 The size of each division (y direction)
	 * @return The index to areas where the map element fits.
	 */
	private static int pickArea(MapArea[] areas, MapElement e,
			int xbase30, int ybase30,
			int nx, int ny,
			int dx30, int dy30)
	{
		int x = e.getLocation().getHighPrecLon();
		int y = e.getLocation().getHighPrecLat();
		int xcell = (x - xbase30) / dx30;
		int ycell = (y - ybase30) / dy30;

		if (xcell < 0) {
			log.info("xcell was", xcell, "x", x, "xbase", xbase30);
			xcell = 0;
		}
		if (ycell < 0) {
			log.info("ycell was", ycell, "y", y, "ybase", ybase30);
			ycell = 0;
		}
		
		if (xcell >= nx)
			xcell = nx - 1;
		if (ycell >= ny)
			ycell = ny - 1;

		if (log.isDebugEnabled()) {
			log.debug("adding", e.getLocation(), "to", xcell, "/", ycell,
					areas[xcell * ny + ycell].getBounds());
		}
		return xcell * ny + ycell;
	}

	/**
	 * Spit the polygon into areas
	 *
	 * @param areas The available areas to choose from.
	 * @param e The map element.
	 * @param used flag vector to say area has been added to.
	 */
	private void splitIntoAreas(MapArea[] areas, MapShape e)
	{
		if (areas.length == 1) { // this happens quite a lot
			areas[0].addShape(e);
			return;
		}
		// quick check if bbox of shape lies fully inside one of the areas
		Area shapeBounds = e.getBounds();

		// this is worked out at standard precision, along with Area.contains() and so can get
		// tricky problems as it might not really be fully within the area.
		// so: pretend the shape is a touch bigger. Will get the optimisation most of the time
		// and in the boundary cases will fall into the precise code.
		int xtra = 2;
		// However, if the shape is significantly larger than the error margin (ie most of it
		// should be in this area) I don't see any problem in letting it expand a little bit out
		// of the area.
		// This avoids very small polygons being left in the adjacent areas, which ShapeMergeFilter
		// notices with an debug message and then output filters probably chuck away.
		if (Math.min(shapeBounds.getWidth(), shapeBounds.getHeight()) > 8)
			xtra = -2; // pretend shape is smaller

		shapeBounds = new Area(shapeBounds.getMinLat()-xtra,
				       shapeBounds.getMinLong()-xtra,
				       shapeBounds.getMaxLat()+xtra,
				       shapeBounds.getMaxLong()+xtra);
		for (int areaIndex = 0; areaIndex < areas.length; ++areaIndex) {
			if (areas[areaIndex].getBounds().contains(shapeBounds)) {
				areas[areaIndex].addShape(e);
				return;
			}
		}

		// Shape crosses area(s), we have to split it

		if (areasHashMap == null)
			areasHashMap = new Long2ObjectOpenHashMap<>();

		if (areas.length == 2) { // just divide along the line between the two areas
			int dividingLine = 0;
			boolean isLongitude = false;
			boolean commonLine = true;
			if (areas[0].getBounds().getMaxLat() == areas[1].getBounds().getMinLat()) {
				dividingLine = areas[0].getBounds().getMaxLat();
				isLongitude = false;
			} else if (areas[0].getBounds().getMaxLong() == areas[1].getBounds().getMinLong()) {
				dividingLine = areas[0].getBounds().getMaxLong();
				isLongitude = true;
			} else {
				commonLine = false;
				log.error("Split into 2 expects shared edge between the areas");
			}
			if (commonLine) {
				List<List<Coord>> lessList = new ArrayList<>(), moreList = new ArrayList<>();
				ShapeSplitter.splitShape(e.getPoints(), dividingLine << Coord.DELTA_SHIFT, isLongitude, lessList, moreList, areasHashMap);
				for (List<Coord> subShape : lessList) {
					MapShape s = e.copy();
					s.setPoints(subShape);
					s.setClipped(true);
					areas[0].addShape(s);
				}
				for (List<Coord> subShape : moreList) {
					MapShape s = e.copy();
					s.setPoints(subShape);
					s.setClipped(true);
					areas[1].addShape(s);
				}
				return;
			}
		}

		for (int areaIndex = 0; areaIndex < areas.length; ++areaIndex) {
			List<List<Coord>> subShapePoints = ShapeSplitter.clipToBounds(e.getPoints(), areas[areaIndex].getBounds(), areasHashMap);
			for (List<Coord> subShape : subShapePoints) {
				MapShape s = e.copy();
				s.setPoints(subShape);
				s.setClipped(true);
				areas[areaIndex].addShape(s);
			}
		}
	}

	/**
	 * @return true if this area contains any data
	 */
	public boolean hasData() {
		if (points.isEmpty() && lines.isEmpty() && shapes.isEmpty())
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MapArea [res=" + areaResolution + ", width=" + bounds.getWidth() + ", height=" + bounds.getHeight()
				+ ", sizes=" + Arrays.toString(sizes) + "]";
	}
	
	
}
