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
		ShapeHelper toMerge = new ShapeHelper(toAdd.getPoints());
		List<Coord>merged = null;
		IntArrayList positionsToCheck = new IntArrayList();
		for (ShapeHelper sh:list){
			int shSize = sh.getPoints().size();
			int toMergeSize = toMerge.getPoints().size();
			if (shSize + toMergeSize - 3 >= PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				// don't merge because merged polygon would be split again
				result.add(sh);
				continue;
			}
			positionsToCheck.clear();
			for (Coord co: sh.getPoints())
				co.resetShapeCount();
			for (Coord co: toMerge.getPoints()){
				co.resetShapeCount();
			}
			// increment the shape counter for all points, but
			// only once 
			int numDistinctPoints = 0;
			
			for (Coord co : sh.getPoints()) {
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
				int usage = toMerge.getPoints().get(i).getShapeCount();			
				if (usage > 0)
					positionsToCheck.add(i);
			}
			if (positionsToCheck.isEmpty()){
				result.add(sh);
				continue;
			}
			if (positionsToCheck.size() + 1 >= toMergeSize){
				// all points are identical, might be a duplicate
				// or a piece that fills a hole 
				if (shSize == toMergeSize){
					// it is a duplicate, we can ignore it
					log.warn("ignoring duplicate shape with id " + toAdd.id + " at " +  toAdd.getPoints().get(0).toOSMURL() + " with type " + GType.formatType(type) + " for resolution " + resolution);
					continue;
				}
			}
			for (int i: positionsToCheck)
				toMerge.getPoints().get(i).incShapeCount();
			
			if (positionsToCheck.size() < 2){
//				GpxCreator.createGpx("e:/ld/sh", sh.getPoints());
//				GpxCreator.createGpx("e:/ld/toadd", toMerge.getPoints());
				long dd = 4; 
			}
			// TODO: merge also when only one point is shared
			// TODO: merge if sh shares all points with toMerge (but has less points) 
			boolean stopSearch = false;
			int rotation = 0;
			while (rotation < shSize && sh.getPoints().get(rotation).getShapeCount() >= 2)
				rotation++;
			
			if (rotation >= shSize){
				rotation = 0;
				Coord goodPointToStart = toMerge.getPoints().get(positionsToCheck.getInt(0));
				while (rotation < shSize && sh.getPoints().get(rotation) != goodPointToStart)
					rotation++;
			}
			
			if (rotation > 0){
				sh.points.remove(shSize-1);
				Collections.rotate(sh.points, -rotation);
				sh.points.add(sh.points.get(0));
			}
			int start = 0;
			while (start < shSize && sh.getPoints().get(start).getShapeCount() < 2)
				start++;
			for (int i = start; i < shSize; i++) {
				if (stopSearch)
					break;
				Coord other = sh.getPoints().get(i);
				if (other.getShapeCount() < 2){
					continue;
				}
				for (int j : positionsToCheck){
					if (stopSearch)
						break;
					Coord toAddCurrent = toMerge.getPoints().get(j);
					if (other == toAddCurrent){
						// shapes share one point
						Coord otherNext = sh.getPoints().get((i == shSize-1) ? 1: i + 1);
						Coord toAddPrev = toMerge.getPoints().get((j == 0) ? toMergeSize-2 : j - 1);
						Coord toAddNext = toMerge.getPoints().get((j == toMergeSize-1) ? 1: j + 1);
						if (positionsToCheck.size() > 10){
							long dd = 4;
						}
						if (otherNext == toAddNext){
							// shapes share an edge, one is clockwise, one is not
							List<Coord> reversed = new ArrayList<>(toMerge.getPoints());
							Collections.reverse(reversed);
							int jr = reversed.size()-1 - j;
							merged = combine(sh.getPoints(), reversed, i, jr, otherNext);
						}
						else if (otherNext == toAddPrev){
							merged = combine(sh.getPoints(), toMerge.getPoints(), i, j, otherNext);
						}
						if (merged == null)
							continue;
						stopSearch = true;
						if (positionsToCheck.size() > 10){
//							 GpxCreator.createGpx("e:/ld/sh", sh.getPoints());
//							 GpxCreator.createGpx("e:/ld/toadd", toMerge.getPoints());
//							 GpxCreator.createGpx("e:/ld/merged", merged);
							 log.error("warning: large number of identical points in two shapes with equal type " + GType.formatType(type) + " near " + otherNext.toOSMURL());
						}
						if (merged.size() < 4 || merged.get(0) != merged.get(merged.size()-1)){
							log.error("merging shapes failed for shapes near " + otherNext.toOSMURL() + " (maybe duplicate shapes?)");
							merged = null;
							continue;
						} 
						ShapeHelper shm = new ShapeHelper(merged);
						if (Math.abs(shm.areaSize) != Math.abs(sh.areaSize) + Math.abs(toMerge.areaSize)){
							log.error("merging shapes failed for shapes near " + otherNext.toOSMURL() + " (maybe overlapping shapes?)");
							merged = null;
							continue;
						} else 
							toMerge = shm;
					}
				}
			}
			if (merged == null)
				result.add(sh);
			merged = null;
		}
		if (merged == null)
			result.add(toMerge);
		if (result.size() > list.size()+1 )
			log.error("result list size is wrong " + list.size() + " -> " + result.size());
		return result;
	}

	/**
	 * Combine to shapes that share at least one edge 
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
	
	private class ShapeHelper{
		final private List<Coord> points;
		long id;
		long areaSize;
		
		public ShapeHelper(MapShape shape) {
			this.points = shape.getPoints();
			this.id = shape.getOsmid();
			prep();
		}

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
			prep();
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
		void prep() {
			assert points.size() >= 4;
			assert points.get(0) == points.get(points.size()-1);
			Iterator<Coord> polyIter = points.iterator();
			Coord c2 = polyIter.next();
			while (polyIter.hasNext()) {
				Coord c1 = c2;
				c2 = polyIter.next();
				areaSize += (long) (c2.getHighPrecLon() + c1.getHighPrecLon())
						* (c1.getHighPrecLat() - c2.getHighPrecLat());
			}
		}

		
	}
}

