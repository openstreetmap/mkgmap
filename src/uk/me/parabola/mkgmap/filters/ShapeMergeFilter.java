/*
 * Copyright (C) 2014.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.filters;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.osmstyle.WrongAngleFixer;
import uk.me.parabola.mkgmap.reader.osm.GType;
//import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.MultiHashMap;


/**
 * Merge shapes with same type and resolution attributes if they share 
 * identical points.
 * @author GerdP
 *
 */
public class ShapeMergeFilter{
	private static final Logger log = Logger.getLogger(ShapeMergeFilter.class);
	private final int resolution;

	public ShapeMergeFilter(int resolution) {
		this.resolution = resolution;
	}

	public List<MapShape> merge(List<MapShape> shapes, int subdivId) {
		if (shapes.size() <= 1)
			return shapes;
		
		MultiHashMap<Integer, Map<MapShape, List<ShapeHelper>>> topMap = new MultiHashMap<Integer, Map<MapShape,List<ShapeHelper>>>();
		List<MapShape> mergedShapes = new ArrayList<MapShape>();
		int i = 0;
		for (MapShape shape: shapes) {
			if (shape.getMinResolution() > resolution || shape.getMaxResolution() < resolution)
				continue;
			if (shape.getPoints().size() > PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				mergedShapes.add(shape);
				continue;
			}
//			GpxCreator.createGpx("e:/ld/tm_" + i++, shape.getPoints());
			List<Map<MapShape, List<ShapeHelper>>> sameTypeList = topMap.get(shape.getType());
			ShapeHelper sh = new ShapeHelper(shape);
			if (sameTypeList.isEmpty()){
				Map<MapShape, List<ShapeHelper>> lowMap = new LinkedHashMap<MapShape, List<ShapeHelper>>();
				ArrayList<ShapeHelper> list = new ArrayList<ShapeHelper>();
				list.add(sh);
				lowMap.put(shape, list);
				topMap.add(shape.getType(),lowMap);
				continue;
			}
			for (Map<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
				boolean added = false;
				for (MapShape ms: lowMap.keySet()){
					if (ms.isSimilar(shape)){
						List<ShapeHelper> list = lowMap.get(ms);
						list = addWithoutCreatingHoles(list, sh, ms.getType());
						lowMap.put(ms, list);
						added = true;
						break;
					}
				}
				if (!added){
					ArrayList<ShapeHelper> list = new ArrayList<ShapeHelper>();
					list.add(sh);
					lowMap.put(shape, list);
				}
			}
		}
		i = 0;
		for (List<Map<MapShape, List<ShapeHelper>>> sameTypeList : topMap.values()){
			i++;
			int j = 0;
			for (Map<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
				j++;
				int k = 0;
				Iterator<Entry<MapShape, List<ShapeHelper>>> iter = lowMap.entrySet().iterator();
				while (iter.hasNext()){
					k++;
					int l = 0;
					Entry<MapShape, List<ShapeHelper>> item = iter.next();
					MapShape ms = item.getKey();
					List<ShapeHelper> shapeHelpers = item.getValue();
					for (ShapeHelper sh:shapeHelpers){
						l++;
						MapShape newShape = ms.copy();
						assert sh.getPoints().get(0) == sh.getPoints().get(sh.getPoints().size()-1);
						if (sh.id == 0){
							List<Coord> optimizedPoints = WrongAngleFixer.fixAnglesInShape(sh.getPoints());
							newShape.setPoints(optimizedPoints);
						} else
							newShape.setPoints(sh.getPoints());
						mergedShapes.add(newShape);
					}
				}
			}
		}
		return mergedShapes;
	}

	/**
	 * Try to merge a shape with one or more of the shapes in the list.
	 *  If it cannot be merged, it is added to the list.   
	 * @param list
	 * @param toAdd
	 * @return
	 */
	private List<ShapeHelper> addWithoutCreatingHoles(List<ShapeHelper> list,
			final ShapeHelper toAdd, final int type) {
		assert toAdd.getPoints().size() > 3;
		List<ShapeHelper> result = new ArrayList<ShapeHelper>();
		ShapeHelper shNew = new ShapeHelper(toAdd.getPoints());
		List<Coord>merged = null;
		IntArrayList positionsToCheck = new IntArrayList();
		for (ShapeHelper shOld:list){
			if (shOld.bounds.intersects(shNew.bounds) == false){
				result.add(shOld);
				continue;
			}
			int shSize = shOld.getPoints().size();
			int toMergeSize = shNew.getPoints().size();
			if (shSize + toMergeSize - 3 >= PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				// don't merge because merged polygon would be split again
				result.add(shOld);
				continue;
			}
			positionsToCheck.clear();
			for (Coord co: shOld.getPoints())
				co.resetShapeCount();
			for (Coord co: shNew.getPoints()){
				co.resetShapeCount();
			}
			// increment the shape counter for all points, but
			// only once 
			int numDistinctPoints = 0;
			
			for (Coord co : shOld.getPoints()) {
				if (co.getShapeCount() == 0){
					co.incShapeCount();
					++numDistinctPoints;
				}
			}
			if (numDistinctPoints+1 != shSize){
				// shape is self-intersecting or has repeated identical points
				long dd = 4;
			}
			for (int i = 0; i+1 < toMergeSize; i++){ 
				int usage = shNew.getPoints().get(i).getShapeCount();			
				if (usage > 0)
					positionsToCheck.add(i);
			}
			if (positionsToCheck.isEmpty()){
				result.add(shOld);
				continue;
			}
			Coord firstSharedInNew = shNew.getPoints().get(positionsToCheck.getInt(0));
			if (positionsToCheck.size() > 10){
				 log.error("warning: high number of identical points ("+positionsToCheck.size()+ ") in two shapes with equal type " + GType.formatType(type) + " near " + firstSharedInNew.toOSMURL());
			}
			if (positionsToCheck.size() + 1 >= toMergeSize){
				// all points are identical, might be a duplicate
				// or a piece that fills a hole 
				if (shSize == toMergeSize && Math.abs(shOld.areaTestVal) == Math.abs(shNew.areaTestVal)){ 
					// it is a duplicate, we can ignore it
					log.warn("ignoring duplicate shape with id " + toAdd.id + " at " +  toAdd.getPoints().get(0).toOSMURL() + " with type " + GType.formatType(type) + " for resolution " + resolution);
					continue;
				}
			}
			
			for (int i: positionsToCheck)
				shNew.getPoints().get(i).incShapeCount();
			
			boolean stopSearch = false;
			int rotation = 0;
			while (rotation < shSize && shOld.getPoints().get(rotation).getShapeCount() >= 2)
				rotation++;
			
			if (rotation >= shSize){
				rotation = 0;
				Coord goodPointToStart = firstSharedInNew;
				while (rotation < shSize && shOld.getPoints().get(rotation) != goodPointToStart)
					rotation++;
			}
			
			if (rotation > 0){
				shOld.getPoints().remove(shSize-1);
				Collections.rotate(shOld.getPoints(), -rotation);
				shOld.points.add(shOld.getPoints().get(0));
			}
			// both clockwise or both ccw ?
			boolean sameDir = shOld.areaTestVal > 0 && shNew.areaTestVal > 0 || shOld.areaTestVal < 0 && shNew.areaTestVal < 0;
			int start = 0;
			while (start < shSize && shOld.getPoints().get(start).getShapeCount() < 2)
				start++;
			if (positionsToCheck.size() < 2){
				merged = combineOneSharedPoint(shOld.getPoints(), shNew.getPoints(), start, positionsToCheck.getInt(0), firstSharedInNew, sameDir);
				stopSearch = true;
			}
			for (int i = start; i < shSize; i++) {
				if (stopSearch)
					break;
				Coord other = shOld.getPoints().get(i);
				if (other.getShapeCount() < 2){
					continue;
				}
				for (int j : positionsToCheck){
					if (stopSearch)
						break;
					Coord toAddCurrent = shNew.getPoints().get(j);
					if (other == toAddCurrent){
						// shapes share one point
						Coord otherNext = shOld.getPoints().get((i == shSize-1) ? 1: i + 1);
						Coord toAddPrev = shNew.getPoints().get((j == 0) ? toMergeSize-2 : j - 1);
						Coord toAddNext = shNew.getPoints().get((j == toMergeSize-1) ? 1: j + 1);
						if (otherNext == toAddNext){
							// shapes share an edge, one is clockwise, one is not
							List<Coord> reversed = new ArrayList<>(shNew.getPoints());
							Collections.reverse(reversed);
							int jr = reversed.size()-1 - j;
							merged = combine(shOld.getPoints(), reversed, i, jr, otherNext);
						}
						else if (otherNext == toAddPrev){
							merged = combine(shOld.getPoints(), shNew.getPoints(), i, j, otherNext);
						}
						if (merged == null)
							continue;
						stopSearch = true;
					}
				}
			}
			if (merged != null){
				ShapeHelper shm = new ShapeHelper(merged);
				if (Math.abs(shm.areaTestVal) != Math.abs(shOld.areaTestVal) + Math.abs(shNew.areaTestVal)){
					log.warn("merging shapes skipped for shapes near " + firstSharedInNew.toOSMURL() + " (maybe overlapping shapes?)");
					merged = null;
				} else 
					shNew = shm;
			}
			if (merged == null)
				result.add(shOld);
			merged = null;
		}
		if (merged == null)
			result.add(shNew);
		if (result.size() > list.size()+1 )
			log.error("result list size is wrong " + list.size() + " -> " + result.size());
		return result;
	}

	/**
	 * Combine two shapes that share at least one edge 
	 * @param shape1 
	 * @param shape2
	 * @param posIn1
	 * @param posIn2
	 * @param stop
	 * @return merged shape
	 */
	private List<Coord> combine(List<Coord> shape1, List<Coord> shape2, int posIn1, int posIn2, Coord stop){
		int n1 = shape1.size();
		int n2 = shape2.size();
		List<Coord> merged = new ArrayList<Coord>(n1 + n2 - 3);
		if (posIn1+1 < n1)
			merged.addAll(shape1.subList(0, posIn1+1));
		else 
			merged.addAll(shape1.subList(1, posIn1+1));
		int k = posIn2+1;
		while (true){
			if (k == n2)
				k = 1;
			Coord co =  shape2.get(k++);
			if (co == stop)
				break;
			merged.add(co);
		}
		k = posIn1 + 2;
		while(k < n1 && shape1.get(k) == merged.get(merged.size()-1)){
			// remove duplicated points
			k++;
			merged.remove(merged.size()-1);
		}
		if (k-1 < n1)
			merged.addAll(shape1.subList(k-1, n1));
		else 
			merged.add(merged.get(0));
		
		return merged;
	}
	
	/**
	 * Combine two shapes that share only one point 
	 * @param shape1 
	 * @param shape2
	 * @param posIn1
	 * @param posIn2
	 * @param shared
	 * @param sameDir 
	 * @return merged shape
	 */
	private List<Coord> combineOneSharedPoint(List<Coord> shape1, List<Coord> shape2, int posIn1, int posIn2, Coord shared, boolean sameDir){
		int n1 = shape1.size();
		int n2 = shape2.size();
		List<Coord> merged = new ArrayList<Coord>(n1 + n2);
		merged.addAll(shape1.subList(0, n1-1));
		Collections.rotate(merged, -posIn1);
		
		merged.addAll(shape2.subList(0, n2-1));
		Collections.rotate(merged.subList(n1-1, merged.size()),-posIn2);
		merged.add(merged.get(0));
		if (!sameDir)
			Collections.reverse(merged.subList(n1-1,merged.size()));
		assert merged.get(0) == shared;
		return merged;
	}
	
	private class ShapeHelper{
		final private List<Coord> points;
		long id; // TODO: remove debugging aid
		long areaTestVal;
		private final Area bounds;

		public ShapeHelper(MapShape shape) {
			this.points = shape.getPoints();
			this.id = shape.getOsmid();
			areaTestVal = calcAreaSizeTestVal(points);
			bounds = new Area(shape.getBounds().getMinLat(), 
					shape.getBounds().getMinLong(), 
					shape.getBounds().getMaxLat(), 
					shape.getBounds().getMaxLong());
		}

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
			areaTestVal = calcAreaSizeTestVal(points);
			bounds = prep();
		}

		public List<Coord> getPoints() {
//			return Collections.unmodifiableList(points); // too slow, use only while testing
			return points;
		}
		/**
		 * Calculates a unitless number that gives a value for the size
		 * of the area and the direction (clockwise/ccw)
		 * 
		 */
		Area prep() {
			int minLat = Integer.MAX_VALUE;
			int maxLat = Integer.MIN_VALUE;
			int minLon = Integer.MAX_VALUE;
			int maxLon = Integer.MIN_VALUE;
			for (Coord co: points) {
				if (co.getLatitude() > maxLat)
					maxLat = co.getLatitude();
				if (co.getLatitude() < minLat)
					minLat = co.getLatitude();
				if (co.getLongitude() > maxLon)
					maxLon = co.getLongitude();
				if (co.getLongitude() < minLon)
					minLon = co.getLongitude();
			}
			return new Area(minLat, minLon, maxLat, maxLon);
		}
	}
	
	/**
	 * Calculate the high precision area size test value.  
	 * @param points
	 * @return area size in high precision map units * 2.
	 * The value is >= 0 if the shape is clockwise, else < 0   
	 */
	public static long calcAreaSizeTestVal(List<Coord> points){
		assert points.size() >= 4;
		assert points.get(0) == points.get(points.size()-1);
		Iterator<Coord> polyIter = points.iterator();
		Coord c2 = polyIter.next();
		long signedAreaSize = 0;
		while (polyIter.hasNext()) {
			Coord c1 = c2;
			c2 = polyIter.next();
			signedAreaSize += (long) (c2.getHighPrecLon() + c1.getHighPrecLon())
					* (c1.getHighPrecLat() - c2.getHighPrecLat());
		}
		return signedAreaSize;
	}
}

