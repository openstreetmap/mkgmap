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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.osmstyle.WrongAngleFixer;
import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.util.Java2DConverter;


/**
 * Merge shapes with same Garmin type and similar attributes if they have common 
 * points. This reduces the number of shapes as well as the number of points.
 * @author GerdP
 *
 */
public class ShapeMergeFilter{
	private static final Logger log = Logger.getLogger(ShapeMergeFilter.class);
	private final int resolution;
	private final static ShapeHelper DUP_SHAPE = new ShapeHelper(new ArrayList<Coord>(0)); 
	private final boolean orderByDecreasingArea;

	public ShapeMergeFilter(int resolution, boolean orderByDecreasingArea) {
		this.resolution = resolution;
		this.orderByDecreasingArea = orderByDecreasingArea;
	}

	/**
	 * Merge shapes that are similar and have identical points.
	 * @param shapes list of shapes
	 * @return list of merged shapes (might be identical to original list)
	 */
	public List<MapShape> merge(List<MapShape> shapes) {
		if (shapes.size() <= 1)
			return shapes;
		List<MapShape> mergedShapes = new ArrayList<>();
		List<MapShape> usableShapes = new ArrayList<>();
		for (MapShape shape: shapes) {
			if (shape.getMinResolution() > resolution || shape.getMaxResolution() < resolution)
				continue;
			if (shape.getPoints().get(0) != shape.getPoints().get(shape.getPoints().size()-1)){
				// should not happen here
				log.error("shape is not closed with identical points", shape.getOsmid(),
					  shape.getPoints().get(0).toOSMURL());
				mergedShapes.add(shape);
				continue;
			}
			usableShapes.add(shape);
		}
		if (usableShapes.size() < 2)
			return mergedShapes;
		
		Comparator<MapShape> comparator = new MapShapeComparator();
		usableShapes.sort(comparator);
		int p1 = 0;
		MapShape s1 = usableShapes.get(0);
		for (int i = 1; i < usableShapes.size(); i++) {
			if (comparator.compare(s1, usableShapes.get(i)) == 0)
				continue;
			mergeSimilar(usableShapes.subList(p1, i), mergedShapes);
			s1 = usableShapes.get(i);
			p1 = i;
		}
		if (p1 < usableShapes.size())
			mergeSimilar(usableShapes.subList(p1, usableShapes.size()), mergedShapes);
		return mergedShapes;
	}
	
	/**
	 * Merge similar shapes.
	 * @param similar list of similar shapes
	 * @param mergedShapes list to which the shapes are added 
	 */
	private void mergeSimilar(List<MapShape> similar, List<MapShape> mergedShapes) {
		if (similar.size() == 1) {
			mergedShapes.addAll(similar);
			return;
		}
		List<ShapeHelper> list = new ArrayList<>();
		MapShape s1 = similar.get(0);
		for (MapShape ms : similar) {
			ShapeHelper sh = new ShapeHelper(ms.getPoints());
			sh.id = ms.getOsmid();
			list.add(sh);
		}
		tryMerge(s1, list);
		for (ShapeHelper sh : list) {
			MapShape newShape = s1.copy();

			assert sh.getPoints().get(0) == sh.getPoints().get(sh.getPoints().size() - 1);
			if (sh.id == 0) {
				// this shape is the result of a merge
				List<Coord> optimizedPoints = WrongAngleFixer.fixAnglesInShape(sh.getPoints());
				if (optimizedPoints.isEmpty())
					continue;
				newShape.setPoints(optimizedPoints);
				newShape.setOsmid(FakeIdGenerator.makeFakeId());
			} else {
				newShape.setPoints(sh.getPoints());
				newShape.setOsmid(sh.id);
			}
			mergedShapes.add(newShape);
		}
	}

	/**
	 * Merge ShapeHelpers. Calls itself recursively.
	 * @param pattern a MapShape
	 * @param similarShapes {@link ShapeHelper} instances created from similar {@link MapShape}.
	 * This list is modified if shapes were merged.
	 */
	private void tryMerge(MapShape pattern, List<ShapeHelper> similarShapes) {
		if (similarShapes.size() <= 1)
			return;

		List<ShapeHelper> noMerge = new ArrayList<>();
		// abuse highway count to find identical points in different shapes
		similarShapes.forEach(sh -> sh.getPoints().forEach(Coord::resetHighwayCount));
		for (ShapeHelper sh : similarShapes) {
			for (int i = 1; i < sh.getPoints().size(); i++) {
				sh.getPoints().get(i).incHighwayCount();
			}
		}
		
		// points with count > 1 are probably shared by different shapes, collect the shapes
		IdentityHashMap<Coord, BitSet> coord2Shape = new IdentityHashMap<>();
		for (int i = 0; i < similarShapes.size(); i++) {
			ShapeHelper sh = similarShapes.get(i);
			boolean hit = false;
			for (int j = 1; j < sh.getPoints().size(); j++) {
				Coord c = sh.getPoints().get(j);
				if (c.getHighwayCount() > 1) {
					hit = true;
					BitSet set = coord2Shape.get(c);
					if (set == null) {
						set = new BitSet();
						coord2Shape.put(c, set);
					}
					set.set(i);
				}
			}
			
			if (!hit) {
				// shape shares no point with others
				noMerge.add(sh);
			}
		}
		if (coord2Shape.isEmpty()) {
			// nothing to do
			return;
		}
		
		List<ShapeHelper> next = new ArrayList<>();
		boolean merged = false;
		BitSet done = new BitSet();
		BitSet delayed = new BitSet();
		
		// loop that makes sure that shapes are processed in a predictable order
		for (int i = 0; i < similarShapes.size(); i++) {
			BitSet all = new BitSet();
			for (BitSet bs : coord2Shape.values()) {
				if (bs.get(i))
					all.or(bs);
			}
			if (all.cardinality() <= 1) {
				if (!all.isEmpty())
					delayed.set(i);
				continue;
			}
			all.andNot(done);
			if (all.isEmpty())
				continue;
			if (done.get(i)) {
				long dd =4;
			}
			
			List<ShapeHelper> result = new ArrayList<>();
			for (int j = all.nextSetBit(0); j >= 0; j = all.nextSetBit(j+1)) {
				ShapeHelper sh = similarShapes.get(j);
				int oldSize = result.size();
				result = addWithConnectedHoles(result, sh, pattern.getType());
				if (result.size() < oldSize + 1) {
					merged = true;
					log.debug("shape with id", sh.id, "was merged", (oldSize + 1 - result.size()),
							" time(s) at resolution", resolution);
				}
			}
			done.or(all);
			next.addAll(result);
		}
		
		coord2Shape = null;
		delayed.andNot(done);
		if (!delayed.isEmpty()) {
			for (int i = delayed.nextSetBit(0); i >= 0; i = delayed.nextSetBit(i+1)) {
				noMerge.add(similarShapes.get(i));
			}
		}
		similarShapes.clear();
		similarShapes.addAll(noMerge);
		
		if (merged) {
			tryMerge(pattern, next);
		}
		similarShapes.addAll(next);
	}


	/**
	 * Try to merge a shape with one or more of the shapes in the list.
	 *  If it cannot be merged, it is added to the list.
	 *  Holes in shapes are connected with the outer lines,
	 *  so no following routine must use {@link Java2DConverter}
	 *  to process these shapes.
	 * @param list list of shapes with equal type
	 * @param toAdd new shape
	 * @param type garmin type of pattern MapShape
	 * @return new list of shapes, this might contain fewer (merged) elements
	 */
	private List<ShapeHelper> addWithConnectedHoles(List<ShapeHelper> list,
			final ShapeHelper toAdd, final int type) {
		assert toAdd.getPoints().size() > 3;
		List<ShapeHelper> result = new ArrayList<>(list.size() + 1);
		ShapeHelper shNew = new ShapeHelper(toAdd);
		for (ShapeHelper shOld : list) {
			ShapeHelper mergeRes = tryMerge(shOld, shNew);
			if (mergeRes == shOld){
				result.add(shOld);
				continue;
			} else if (mergeRes != null){
				shNew = mergeRes;
			}
			if (shNew == DUP_SHAPE){
				log.warn("ignoring duplicate shape with id", toAdd.id, "at",  toAdd.getPoints().get(0).toOSMURL(), "with type", GType.formatType(type), "for resolution", resolution);
				return list; // nothing to do
			}
		}
		if (shNew != null && shNew != DUP_SHAPE)
			result.add(shNew);
		if (result.size() > list.size()+1 )
			log.error("result list size is wrong", list.size(), "->", result.size());
		return result;
	}

	/**
	 * Find out if two shapes have common points. If yes, merge them.
	 * @param sh1 1st shape1
	 * @param sh2 2st shape2
	 * @return merged shape or 1st shape if no common point found or {@code dupShape} 
	 * if both shapes describe the same area. 
	 */
	private static ShapeHelper tryMerge(ShapeHelper sh1, ShapeHelper sh2) {
		
		// both clockwise or both ccw ?
		boolean sameDir = sh1.areaTestVal > 0 && sh2.areaTestVal > 0 || sh1.areaTestVal < 0 && sh2.areaTestVal < 0;
		
		List<Coord> points1, points2;
		if (sh2.getPoints().size()> sh1.getPoints().size()){
			points1 = sh2.getPoints();
			points2 = sh1.getPoints();
		} else {
			points1 = sh1.getPoints();
			points2 = sh2.getPoints();
		}
		// find all coords that are common in the two shapes 
		IntArrayList sh1PositionsToCheck = new IntArrayList();
		IntArrayList sh2PositionsToCheck = new IntArrayList();

		findCommonCoords(points1, points2, sh1PositionsToCheck, sh2PositionsToCheck); 		
		if (sh1PositionsToCheck.isEmpty()){
			return sh1;
		}
		if (sh2PositionsToCheck.size() + 1 >= points2.size()){
			// all points are identical, might be a duplicate
			// or a piece that fills a hole 
			if (points1.size() == points2.size() && Math.abs(sh1.areaTestVal) == Math.abs(sh2.areaTestVal)){ 
				// it is a duplicate, we can ignore it
				// XXX this might fail if one of the shapes is self intersecting
				return DUP_SHAPE;
			}
		}
		List<Coord> merged = null; 
		if (points1.size() + points2.size() - 2*sh1PositionsToCheck.size() < PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
			merged = mergeLongestSequence(points1, points2, sh1PositionsToCheck, sh2PositionsToCheck, sameDir);
			if (merged.isEmpty())
				return DUP_SHAPE;
			if (merged.get(0) != merged.get(merged.size()-1))
				merged = null;
			else if (merged.size() > PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				// don't merge because merged polygon would be split again
				log.info("merge rejected: merged shape has too many points " + merged.size());
				merged = null;
			}
		}
		ShapeHelper shm = null;
		if (merged != null){
			shm = new ShapeHelper(merged);
			if (Math.abs(shm.areaTestVal) != Math.abs(sh1.areaTestVal) + Math.abs(sh2.areaTestVal)){
				log.warn("merging shapes skipped for shapes near", points1.get(sh1PositionsToCheck.getInt(0)).toOSMURL(), 
						"(maybe overlapping shapes?)");
				merged = null;
				shm = null;
			} else {
				if (log.isInfoEnabled()){
					log.info("merge of shapes near",points1.get(sh1PositionsToCheck.getInt(0)).toOSMURL(), 
							"reduces number of points from",(points1.size()+points2.size()),
							"to",merged.size());
				}
			}
		}
		if (shm != null)
			return shm;
		if (merged == null)
			return sh1;
		return null;
	}

	/**
	 * Find the common Coord instances and save their positions for both shapes.
	 * @param s1 shape 1
	 * @param s2 shape 2
	 * @param s1PositionsToCheck will contain common positions in shape 1   
	 * @param s2PositionsToCheck will contain common positions in shape 2
	 */
	private static void findCommonCoords(List<Coord> s1, List<Coord> s2,
			IntArrayList s1PositionsToCheck,
			IntArrayList s2PositionsToCheck) {
		Map<Coord, Integer> s2PosMap = new IdentityHashMap<>(s2.size() - 1);
		
		for (int i = 0; i+1 < s1.size(); i++){
		    Coord co = s1.get(i);
		    co.setPartOfShape2(false);
		}
		for (int i = 0; i+1 < s2.size(); i++){
		    Coord co = s2.get(i);
		    co.setPartOfShape2(true);
		    s2PosMap.put(co, i); 
		}
		
		int start = 0;
		while(start < s1.size()){
			Coord co = s1.get(start);
			if (!co.isPartOfShape2())
				break;
			start++;
		}
		int pos = start+1;
		int tested = 0;
		while(true){
			if (pos+1 >= s1.size())
				pos = 0;
			Coord co = s1.get(pos);
			if (++tested >= s1.size())
				break;
			if (co.isPartOfShape2()){
				s1PositionsToCheck.add(pos);
				Integer posInSh2 = s2PosMap.get(co);
				assert posInSh2 != null;
				s2PositionsToCheck.add(posInSh2);
			}
			pos++;
		}
		return;
	} 	
	
	/**
	 * Finds the longest sequence of common points in two shapes.
	 * @param points1 list of Coord instances that describes the 1st shape 
	 * @param points2 list of Coord instances that describes the 2nd shape
	 * @param sh1PositionsToCheck positions in the 1st shape that are common
	 * @param sh2PositionsToCheck positions in the 2nd shape that are common
	 * @param sameDir true if both shapes are clockwise or both are ccw
	 * @return the merged shape or null if no points are common.
	 */
	private static List<Coord> mergeLongestSequence(List<Coord> points1, List<Coord> points2, IntArrayList sh1PositionsToCheck,
			IntArrayList sh2PositionsToCheck, boolean sameDir) {
		if (sh1PositionsToCheck.isEmpty())
			return null;
		int s1Size = points1.size(); 
		int s2Size = points2.size();
		int longestSequence = 0;
		int startOfLongestSequence = 0;
		int length = 0;
		int start = -1;
		int n1 = sh1PositionsToCheck.size();
		
		assert sh2PositionsToCheck.size() == n1;
		boolean inSequence = false;
		for (int i = 0; i+1 < n1; i++){
			int pred1 = sh1PositionsToCheck.getInt(i);
			int succ1 = sh1PositionsToCheck.getInt(i+1);
			if (Math.abs(succ1-pred1) == 1 || pred1+2 == s1Size && succ1 == 0 || succ1+2 == s1Size && pred1 == 0 ){
				// found sequence in s1
				int pred2 = sh2PositionsToCheck.getInt(i);
				int succ2 = sh2PositionsToCheck.getInt(i+1);
				if (Math.abs(succ2-pred2) == 1 || pred2+2 == s2Size && succ2 == 0 || succ2+2 == s2Size && pred2 == 0 ){
					// found common sequence
					if (start < 0)
						start = i;
					inSequence = true;
					length++; 
				} else {
					inSequence = false;
				}
			} else {
				inSequence = false;
			}
			if (!inSequence){
				if (length > longestSequence){
					longestSequence = length;
					startOfLongestSequence = start;
				}
				length = 0;
				start = -1;
			}
		}
		if (length > longestSequence){
			longestSequence = length;
			startOfLongestSequence = start;
		}
		// now merge the shapes. The longest sequence of common points is removed.
		// The remaining points are connected in the direction of the 1st shape.
		int remaining = s1Size + s2Size - 2*longestSequence -1;
		if (remaining < 3) {
			return Collections.emptyList(); // may happen with self-intersecting duplicated shapes
		}
		List<Coord> merged = new ArrayList<>(remaining);
		int s1Pos = sh1PositionsToCheck.getInt(startOfLongestSequence+longestSequence);
		for (int i = 0; i < s1Size - longestSequence - 1; i++){
			merged.add(points1.get(s1Pos));
			s1Pos++;
			if (s1Pos+1 >= s1Size)
				s1Pos = 0;
		}
		int s2Pos = sh2PositionsToCheck.getInt(startOfLongestSequence);
		int s2Step = sameDir ? 1:-1;
		for (int i = 0; i < s2Size - longestSequence; i++){
			merged.add(points2.get(s2Pos));
			s2Pos += s2Step;
			if (s2Pos < 0) 
				s2Pos = s2Size-2;
			else if (s2Pos+1 >= s2Size)
				s2Pos = 0;
		}
		return merged;
	}
 	
	private static class ShapeHelper{
		final private List<Coord> points;
		long id; 
		long areaTestVal;

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
			areaTestVal = calcAreaSizeTestVal(points);
		}

		public ShapeHelper(ShapeHelper other) {
			this.points = new ArrayList<>(other.getPoints());
			this.areaTestVal = other.areaTestVal;
			this.id = other.id;
		}

		public List<Coord> getPoints() {
			return points;
		}
		
	}

	public final static long SINGLE_POINT_AREA = 1L << Coord.DELTA_SHIFT * 1L << Coord.DELTA_SHIFT;
	
	/**
	 * Calculate the high precision area size test value.  
	 * @param points
	 * @return area size in high precision map units * 2.
	 * The value is >= 0 if the shape is clockwise, else < 0   
	 */
	public static long calcAreaSizeTestVal(List<Coord> points){
		if (points.size() < 4)
			return 0; // straight line cannot enclose an area
		if (points.get(0).highPrecEquals(points.get(points.size()-1)) == false){
			log.error("shape is not closed");
			return 0;
		}
		Iterator<Coord> polyIter = points.iterator();
		Coord c2 = polyIter.next();
		long signedAreaSize = 0;
		while (polyIter.hasNext()) {
			Coord c1 = c2;
			c2 = polyIter.next();
			signedAreaSize += (long) (c2.getHighPrecLon() + c1.getHighPrecLon())
					* (c1.getHighPrecLat() - c2.getHighPrecLat());
		}
		if (Math.abs(signedAreaSize) < SINGLE_POINT_AREA){
			log.debug("very small shape near", points.get(0).toOSMURL(), "signed area in high prec map units:", signedAreaSize );
		}
		return signedAreaSize;
	}
	
	private class MapShapeComparator implements Comparator<MapShape> {
		@Override
		public int compare(MapShape o1, MapShape o2) {
			int d = Integer.compare(o1.getType(), o2.getType());
			if (d != 0) 
				return d;
			d = Boolean.compare(o1.isSkipSizeFilter(), o2.isSkipSizeFilter());
			if (d != 0)
				return d;
			// XXX wasClipped() is ignored here, might be needed if later filters need it  
			if (orderByDecreasingArea) {
				d = Long.compare(o1.getFullArea(), o2.getFullArea());
				if (d != 0)
					return d;
			}
			String n1 = o1.getName();
			String n2 = o2.getName();
			if (n1 == n2)
				return 0;
			if (n1 == null) {
				return (n2 == null) ? 0 : 1;
			}
			if (n2 == null)
				return -1;
			
			return java.text.Collator.getInstance().compare(n1, n2);
		}
	}
}

