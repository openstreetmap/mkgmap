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

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.osmstyle.WrongAngleFixer;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.util.GpxCreator;
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
			if (shape.getPoints().size() > 100){
				mergedShapes.add(shape);
				continue;
			}
			if (shape.getPoints().size() < 10){
				double areaSize = Utils.calcHighPrecAreaSize(shape.getPoints());
				if (areaSize == 0){
					log.warn("shape with id " + shape.getOsmid()  + " has " + shape.getPoints().size() + " points but zero area size, ignoring it");
					continue;
				}
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
						assert sh.points.get(0) == sh.points.get(sh.points.size()-1);
//						int oldSize = sh.points.size();
//						GpxCreator.createGpx("e:/ld/mr_" + resolution+ "_" + subdivId + "_" + ms.getType() + "_"+i+"_"+j+"_"+k+"_"+l+ "m", sh.points);
						WrongAngleFixer.fixAnglesInShape(sh.points);
//						GpxCreator.createGpx("e:/ld/mr_" + resolution+ "_" + subdivId + "_" + ms.getType() + "_"+i+"_"+j+"_"+k+"_"+l+ "f", sh.points);
//						if (oldSize > sh.points.size() + 10){
//							long dd = 4;
//						}
						newShape.setPoints(sh.points);
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
		assert toAdd.points.size() > 3;
		List<ShapeHelper> result = new ArrayList<ShapeHelper>();
		ShapeHelper toMerge = new ShapeHelper(toAdd.points);
		List<Coord>merged = null;
		IntArrayList positionsToCheck = new IntArrayList();
		for (ShapeHelper sh:list){
			int shSize = sh.points.size();
			int toMergeSize = toMerge.points.size();
			if (shSize + toMergeSize - 3 >= PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				// don't merge because merged polygon would be split again
				result.add(sh);
				continue;
			}
			positionsToCheck.clear();
			for (Coord co: sh.points)
				co.resetShapeCount();
			for (Coord co: toMerge.points){
				co.resetShapeCount();
			}
			// increment the shape counter for all points, but
			// only once 
			int numDistinctPoints = 0;
			
			for (Coord co : sh.points) {
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
				int usage = toMerge.points.get(i).getShapeCount();			
				if (usage > 0)
					positionsToCheck.add(i);
			}
			if (positionsToCheck.isEmpty()){
				result.add(sh);
				continue;
			}
			if (positionsToCheck.size() + 1 == toMergeSize){
				// all points are identical, might be a duplicate
				// or a piece that fills a hole 
				if (shSize == toMergeSize){
					// it is a duplicate, we can ignore it
					log.warn("ignoring duplicate shape with id " + toAdd.id + " at " +  toAdd.points.get(0).toOSMURL() + " with type " + GType.formatType(type) + " for resolution " + resolution);
					continue;
				}
			}
			for (int i: positionsToCheck)
				toMerge.points.get(i).incShapeCount();
			
			if (positionsToCheck.size() < 2){
//				GpxCreator.createGpx("e:/ld/sh", sh.points);
//				GpxCreator.createGpx("e:/ld/toadd", toMerge.points);
				long dd = 4; 
			}
			// TODO: merge also when only one point is shared
			boolean stopSearch = false;
			for (int i = 0; i < shSize; i++) {
				if (stopSearch)
					break;
				Coord other = sh.points.get(i);
				if (other.getShapeCount() < 2)
					continue;
				for (int j : positionsToCheck){
					if (stopSearch)
						break;
					Coord toAddCurrent = toMerge.points.get(j);
					if (other == toAddCurrent){
						// shapes share one point
						Coord otherNext = sh.points.get((i == shSize-1) ? 1: i + 1);
						Coord toAddPrev = toMerge.points.get((j == 0) ? toMergeSize-2 : j - 1);
						Coord toAddNext = toMerge.points.get((j == toMergeSize-1) ? 1: j + 1);
						if (otherNext == toAddNext){
							// shapes share an edge, one is clockwise, one is not
							List<Coord> reversed = new ArrayList<>(toMerge.points);
							Collections.reverse(reversed);
							int jr = reversed.size()-1 - j;
							merged = combine(sh.points, reversed, i, jr, otherNext);
						}
						else if (otherNext == toAddPrev){
							merged = combine(sh.points, toMerge.points, i, j, otherNext);
						}
						if (merged == null)
							continue;
						stopSearch = true;
						if (merged.size() < 4 || merged.get(0) != merged.get(merged.size()-1)){
							log.error("merging shapes failed for shapes near " + otherNext.toOSMURL() + " (maybe duplicate shapes?)");
							GpxCreator.createGpx("e:/ld/sh", sh.points);
							GpxCreator.createGpx("e:/ld/toadd", toMerge.points);
							GpxCreator.createGpx("e:/ld/merged", merged);
							merged = null;
						} else {
							toMerge = new ShapeHelper(merged);
						}
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
		merged.addAll(shape1.subList(0, posIn1+1));
		int k = posIn2+1;
		while (true){
			if (k == n2)
				k = 1;
			Coord co =  shape2.get(k++);
			if (co == stop)
				break;
			merged.add(co);
		}
		k = posIn1+2;
		while(k < n1 && shape1.get(k) == merged.get(merged.size()-1)){
			// remove spike
			k++;
			merged.remove(merged.size()-1);
		}
		
		merged.addAll(shape1.subList(k-1, n1));
		return merged;
	}
	
	private class ShapeHelper{
		List<Coord> points;
		long id;
		
		public ShapeHelper(MapShape shape) {
			this.points = shape.getPoints();
			this.id = shape.getOsmid();
		}

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
		}
		
	}
}

