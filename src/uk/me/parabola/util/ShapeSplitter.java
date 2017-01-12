/*
 * Copyright (C) 2014 Gerd Petermann
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
package uk.me.parabola.util;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

// RWB new bits
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

import uk.me.parabola.log.Logger;

public class ShapeSplitter {
	private static final Logger log = Logger.getLogger(ShapeSplitter.class);
	private static final int LEFT = 0;
	private static final int TOP = 1;
	private static final int RIGHT = 2;
	private static final int BOTTOM= 3;

	
	/**
	 * Clip a given shape with a given rectangle. 
	 * @param shape the subject shape to clip
	 * @param clippingRect the clipping rectangle
	 * @return the intersection of the shape and the rectangle 
	 * or null if they don't intersect. 
	 * The intersection may contain dangling edges. 
	 */
	public static Path2D.Double clipShape (Shape shape, Rectangle2D clippingRect) {
		double minX = Double.POSITIVE_INFINITY,minY = Double.POSITIVE_INFINITY, 
				maxX = Double.NEGATIVE_INFINITY,maxY = Double.NEGATIVE_INFINITY;
		PathIterator pit = shape.getPathIterator(null);
		double[] points = new double[512];
		int num = 0;
		Path2D.Double result = null;
		double[] res = new double[6];
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			double x = res[0];
			double y = res[1];
			if (x < minX) minX = x;
			if (x > maxX) maxX = x;
			if (y < minY) minY = y;
			if (y > maxY) maxY = y;
			switch (type) {
			case PathIterator.SEG_LINETO:
			case PathIterator.SEG_MOVETO:
				if (num  + 2 >= points.length) {
					points = Arrays.copyOf(points, points.length * 2);
				}
				points[num++] = x;
				points[num++] = y;
				break;
			case PathIterator.SEG_CLOSE:
				Path2D.Double segment = null;
				if (clippingRect.contains(minX, minY) == false || clippingRect.contains(maxX,maxY) == false){
					Rectangle2D.Double bbox = new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
					segment = clipSinglePathWithSutherlandHodgman (points, num, clippingRect, bbox);
				} else 
					segment = pointsToPath2D(points, num);
				if (segment != null){
					if (result == null)
						result = segment;
					else 
						result.append(segment, false);
				}
				num = 0;
				minX = minY = Double.POSITIVE_INFINITY; 
				maxX = maxY = Double.NEGATIVE_INFINITY;
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}
	
			pit.next();
		}
		return result;
	}

	/**
	 * Convert a list of longitude+latitude values to a Path2D.Double
	 * @param points the pairs
	 * @return the path or null if the path describes a point or line. 
	 */
	public static Path2D.Double pointsToPath2D(double[] points, int num) {
		if (num < 2)
			return null;
		if (points[0] == points[num-2] && points[1] == points[num-1])
			num -= 2;
		if (num < 6)
			return null;
		Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, num / 2 + 2);
		double lastX = points[0], lastY = points[1];
		path.moveTo(lastX, lastY);
		int numOut = 1;
		for (int i = 2; i < num; ){
			double x = points[i++], y = points[i++];
			if (x != lastX || y != lastY){
				path.lineTo(x,y);
				lastX = x; lastY = y;
				++numOut;
			}
		}
		if (numOut < 3)
			return null;
		path.closePath();
		return path;
	}

	//    The Sutherland�Hodgman algorithm Pseudo-Code: 	 
	//	  List outputList = subjectPolygon;
	//	  for (Edge clipEdge in clipPolygon) do
	//	     List inputList = outputList;
	//	     outputList.clear();
	//	     Point S = inputList.last;
	//	     for (Point E in inputList) do
	//	        if (E inside clipEdge) then
	//	           if (S not inside clipEdge) then
	//	              outputList.add(ComputeIntersection(S,E,clipEdge));
	//	           end if
	//	           outputList.add(E);
	//	        else if (S inside clipEdge) then
	//	           outputList.add(ComputeIntersection(S,E,clipEdge));
	//	        end if
	//	        S = E;
	//	     done
	//	  done

	/**
	 * Clip a single path with a given rectangle using the Sutherland-Hodgman algorithm. This is much faster compared to
	 * the area.intersect method, but may create dangling edges. 
	 * @param points	a list of longitude+latitude pairs  
	 * @param num the nnumber of valid values in points 
	 * @param clippingRect the clipping rectangle 
	 * @param bbox the bounding box of the path 
	 * @return the clipped path as a Path2D.Double or null if the result is empty  
	 */
	public static Path2D.Double clipSinglePathWithSutherlandHodgman (double[] points, int num, Rectangle2D clippingRect, Rectangle2D.Double bbox) {
		if (num <= 2 || bbox.intersects(clippingRect) == false){
			return null;
		}
		
		int countVals = num;
		if (points[0] == points[num-2] && points[1] == points[num-1]){
			countVals -= 2;
		}
		double[] outputList = points;
		double[] input;
		
		double leftX = clippingRect.getMinX();
		double rightX = clippingRect.getMaxX();
		double lowerY = clippingRect.getMinY();
		double upperY = clippingRect.getMaxY();
		boolean eIsIn = false, sIsIn = false;
		for (int side = LEFT; side <= BOTTOM; side ++){
			if (countVals < 6)
				return null; // ignore point or line 
			
			boolean skipTestForThisSide;
			switch(side){
			case LEFT: skipTestForThisSide = (bbox.getMinX() >= leftX); break;
			case TOP: skipTestForThisSide = (bbox.getMaxY()  < upperY); break;
			case RIGHT: skipTestForThisSide = (bbox.getMaxX()  < rightX); break;
			default: skipTestForThisSide = (bbox.getMinY() >= lowerY); 
			}
			if (skipTestForThisSide)
				continue;
			
			input = outputList;
			outputList = new double[countVals + 16];
			double sLon = 0,sLat = 0;
			double pLon = 0,pLat = 0; // intersection
			int posIn = countVals - 2; 
			int posOut = 0;
			for (int i = 0; i < countVals+2; i+=2){
				if (posIn >=  countVals)
					posIn = 0;
				double eLon = input[posIn++];
				double eLat = input[posIn++];
				switch (side){
				case LEFT: eIsIn =  (eLon >= leftX); break;
				case TOP: eIsIn =  (eLat < upperY); break;
				case RIGHT: eIsIn =  (eLon < rightX); break;
				default: eIsIn =  (eLat >= lowerY); 
				}
				if (i > 0){
					if (eIsIn != sIsIn){
						// compute intersection
						double slope;
						if (eLon != sLon)
							slope = (eLat - sLat) / (eLon-sLon);
						else slope = 1;
	
						switch (side){
						case LEFT: 
							pLon = leftX;
							pLat = slope *(leftX-sLon) + sLat;
							break;
						case RIGHT: 
							pLon = rightX;
							pLat = slope *(rightX-sLon) + sLat; 
							break;
	
						case TOP: 
							if (eLon != sLon)
								pLon =  sLon + (upperY - sLat) / slope;
							else 
								pLon =  sLon;
							pLat = upperY;
							break;
						default: // BOTTOM
							if (eLon != sLon)
								pLon =  sLon + (lowerY - sLat) / slope;
							else 
								pLon =  sLon;
							pLat = lowerY;
							break;
	
						}
					}
					int toAdd = 0;
					if (eIsIn){
						if (!sIsIn){
							toAdd += 2;
						}
						toAdd += 2;
					}
					else {
						if (sIsIn){
							toAdd += 2;
						}
					}
					if (posOut + toAdd >= outputList.length) {
						// unlikely
						outputList = Arrays.copyOf(outputList, outputList.length * 2);
					}
					if (eIsIn){
						if (!sIsIn){
							outputList[posOut++] = pLon;
							outputList[posOut++] = pLat;
						}
						outputList[posOut++] = eLon;
						outputList[posOut++] = eLat;
					}
					else {
						if (sIsIn){
							outputList[posOut++] = pLon;
							outputList[posOut++] = pLat;
						}
					}
				}
				// S = E
				sLon = eLon; sLat = eLat;
				sIsIn = eIsIn;
			}
			countVals = posOut;
		}
		return pointsToPath2D(outputList, countVals);
	}

/* Dec16/Jan17. Ticker Berkin. New implementation for splitting shapes.

Eventually maybe can be used instead of some of the above and in following code:

done	mkgmap/build/MapArea.java
	mkgmap/filters/PolygonSplitterBase.java
	mkgmap/filters/ShapeMergeFilter.java
	mkgmap/general/AreaClipper.java
	mkgmap/general/PolygonClipper.java
	mkgmap/reader/osm/MultiPolygonRelation.java
	mkgmap/reader/osm/SeaGenerator.java
	mkgmap/reader/osm/boundary/BoundaryConverter.java
	mkgmap/reader/osm/boundary/BoundaryCoverageUtil.java
	mkgmap/reader/osm/boundary/BoundaryDiff.java
	mkgmap/reader/osm/boundary/BoundaryElement.java
	mkgmap/reader/osm/boundary/BoundaryFile2Gpx.java
	mkgmap/reader/osm/boundary/BoundaryQuadTree.java
	mkgmap/reader/osm/boundary/BoundaryRelation.java
	mkgmap/reader/osm/boundary/BoundarySaver.java
	mkgmap/reader/osm/boundary/BoundaryUtil.java
	mkgmap/sea/optional/PrecompSeaGenerator.java
	mkgmap/sea/optional/PrecompSeaMerger.java
	util/ElementQuadTreeNode.java
	util/Java2DConverter.java
	util/QuadTreeNode.java
*/

	/**
	 * closes a shape and appends to list.
	 *
	 * If the shape starts and ends at the same point on the dividing line then
	 * there is no need to close it. Also check for and chuck a spike, which happens
	 * if there is a single point just across the dividing line and the two intersecting
	 * points ended up being the same.
	 *
	 * @param points the shape to process.
	 * @param origList list of shapes to which we append new shapes.
	 */
	private static void closeAppend(List<Coord> points, List<List<Coord>> origList, boolean onDividingLine) {
		Coord firstCoord = points.get(0);
		int lastPointInx = points.size()-1;
		if (firstCoord.highPrecEquals(points.get(lastPointInx))) { // start and end at same point on line
			if (lastPointInx == 2) // just a single point across the line
				return;
			// There should be no need to close the line, but am finding, for shapes that never crossed the
			// dividing line, quite a few that, after splitShapes has rotating the shape by 1, have first and last
			// points highPrecEquals but they are different objects.
			// This means that the original first and last were the same object, but the second and last were highPrecEquals!
			// If left like this, it might be flagged by ShapeMergeFilter.
			// NB: if no coordPool, likely to be different closing object anyway
			if (firstCoord != points.get(lastPointInx)) {
				if (onDividingLine)
					log.error("high prec/diff obj", firstCoord, lastPointInx, onDividingLine, firstCoord.toOSMURL());
				else
					points.set(lastPointInx, firstCoord); // quietly replace with first point
			}
		} else
			points.add(firstCoord); // close it
		origList.add(points);
	} // closeAppend

	/**
	 * Service routine for processLineList. Processes a nested list of holes within a shape or
	 * list of shapes within a hole.
	 *
	 * Recurses to check for and handle the opposite of what has been called to process.
	 *
	 * @param startInx starting point in list.
	 * @param endEnclosed point where starting line ends on dividing line.
	 * @param addHolesToThis if not null, then called from a shape and subtract holes from it
	 * otherwise new shapes within a hole.
	 * @param lineInfo array of lines.
	 * @param origList list of shapes to which we append new shapes.
	 */
	private static int doLines(int startInx, int endEnclosed, MergeCloseHelper addHolesToThis,
				   MergeCloseHelper[] lineInfo, List<List<Coord>> origList) {
		int inx = startInx;
		while (inx < lineInfo.length) {
			MergeCloseHelper thisLine = lineInfo[inx];
			if (thisLine.highPoint > endEnclosed) // only do enclosed items
				break;
			// process any enclosed lines
			inx = doLines(inx+1, thisLine.highPoint, addHolesToThis == null ? thisLine : null, lineInfo, origList);
			if (addHolesToThis == null) // handling list of shapes
				closeAppend(thisLine.points, origList, true);
			else  // handling list of holes
				addHolesToThis.addHole(thisLine);
		}
		return inx;
	} // doLines

	/**
	 * Service routine for splitShape. Takes list of lines and appends distinct shapes
	 * @param newList list of lines that start and end on the dividing line (or orig startPoint)
	 * @param origList list of shapes to which we append new shapes formed from above
	 * @param isLongitude true if dividing on a line of longitude, false if latitude
	 */
	private static void processLineList(List<List<Coord>> newList, List<List<Coord>> origList, boolean isLongitude) {
		if (origList == null) // never wanted this side
			return;
		List<Coord> firstPoly = newList.get(0);
		if (newList.size() == 1) { // single shape that never crossed line
			if (!firstPoly.isEmpty()) // all on this side
				closeAppend(firstPoly, origList, false);
			return;
		}
		// look at last elem in list of lists
		List<Coord> lastPoly = newList.get(newList.size()-1);
		if (lastPoly.isEmpty()) // will be empty if did not return to this side
			newList.remove(newList.size()-1);
		else { // ended up on this side and must have crossed the line
			// so first points are a continuation of last
			lastPoly.addAll(firstPoly);
			newList.remove(0);
			firstPoly = newList.get(0);
		}
		if (newList.size() == 1) { // simple poly that crossed once and back
			closeAppend(firstPoly, origList, true);
			return;
		}
		// Above were the simple cases - probably 99% of calls.

		// Now imagine something like a snake, with a coiled bit, zig-zag bit and
		// a flat area that has been flash shape hacked out of it. Then cut a line
		// through these complex shapes and now need to describe the shapes remaining
		// on one side of the cut. I think the following does this!
		// splitShape has generated a list of lines that start and end on the dividing line.
		// These lines don't cross. Order them by their lowest point on the divider, but note which
		// direction they go. The first (and last) line must define a shape. Starting with this
		// shape; the next line up, if it is within this shape, must define a hole and
		// so is added to the list of points for the shape. For the hole, recurse to
		// handle any shapes enclosed. Repeat until we reach the end of the enclosing
		// space.

		final int numLines = newList.size();
		// make ordered list of more info about the lines that start/end on the dividing line
		MergeCloseHelper[] lineInfo = new MergeCloseHelper[numLines];
		for (int inx = 0; inx < numLines; ++inx)
			lineInfo[inx] = new MergeCloseHelper(newList.get(inx), isLongitude);
		Arrays.sort(lineInfo);

		log.debug("complex ShapeSplit", numLines, "at", firstPoly.get(0).toOSMURL());
		for (MergeCloseHelper el : lineInfo)
			log.debug(el.lowPoint, el.highPoint, el.direction);

		int dummy = doLines(0, Integer.MAX_VALUE, null, lineInfo, origList);
		assert dummy == lineInfo.length;
	} // processLineList


	/**
	 * Helper class for splitShape. Holds information about line.
	 * Sorts array of itself according to lowest point on dividing line.
	 */
	private static class MergeCloseHelper implements Comparable<MergeCloseHelper> {

		List<Coord> points;
		int direction;
		int lowPoint, highPoint;

		MergeCloseHelper(List<Coord> points, boolean isLongitude) {
			this.points = points;
			Coord aCoord = points.get(0);
			int firstPoint = isLongitude ? aCoord.getHighPrecLat() : aCoord.getHighPrecLon();
			aCoord = points.get(points.size()-1);
			int lastPoint = isLongitude ? aCoord.getHighPrecLat() : aCoord.getHighPrecLon();
			this.direction = Integer.signum(lastPoint - firstPoint);
			if (this.direction > 0) {
				this.lowPoint = firstPoint;
				this.highPoint = lastPoint;
			} else {
				this.lowPoint = lastPoint;
				this.highPoint = firstPoint;
			}
		} // MergeCloseHelper

		public int compareTo(MergeCloseHelper other) {
			int cmp = this.lowPoint - other.lowPoint;
			if (cmp != 0)
				return cmp;
			// for same lowPoint, sort highPoint other way around to enclose as much as possible
			cmp = other.highPoint - this.highPoint;
			if (cmp != 0)
				return cmp;
			// pathalogical case where when have same start & end
			log.error("Lines hit divider at same points", this.lowPoint, this.highPoint, this.points.get(0).toOSMURL());
			// %%% should return one with larger area as being less,
			// but I don't think anyone will define an area like this and expect it to work
			// so, for moment:
			return this.direction - other.direction;
		} // compareTo

		void addHole(MergeCloseHelper other) {
			if (other.direction == 0 && other.points.size() == 3)
				return; // spike into this area. cf. closeAppend()
			// shapes must have opposite directions.
			if (this.direction == 0 && other.direction == 0)
				log.error("Direction of shape and hole indeterminate", this.points.get(0).toOSMURL());
			else if (this.direction != 0 && other.direction != 0 && this.direction == other.direction)
				log.error("Direction of shape and hole conflict", this.points.get(0).toOSMURL());
			else if (this.direction < 0 || other.direction > 0) {
				this.points.addAll(other.points);
				if (this.direction == 0)
					this.direction = -1;
			} else { // add at start
				other.points.addAll(this.points);
				this.points = other.points;
				if (this.direction == 0)
					this.direction = +1;
			}
		} // addHole

	} // MergeCloseHelper

	/**
	 * split a shape with a line
	 * @param coords the shape. Must be closed.
	 * @param dividingLine the line in high precision.
	 * @param isLongitude true if above is line of longitude, false if latitude.
	 * @param lessList list of shapes to which we append new shapes on lower/left side of line.
	 * @param moreList list of shapes to which we append new shapes on upper/right side of line.
	 * @param coordPool if not null, hashmap for created coordinates. Will all be on the line.
	 */
	public static void splitShape(List<Coord> coords, int dividingLine, boolean isLongitude,
				      List<List<Coord>> lessList, List<List<Coord>> moreList,
				      Long2ObjectOpenHashMap<Coord> coordPool) {

		List<List<Coord>> newLess = null, newMore = null;
		List<Coord> lessPoly = null, morePoly = null;
		if (lessList != null) {
			newLess = new ArrayList<>();
			lessPoly = new ArrayList<>();
			newLess.add(lessPoly);
		}
		if (moreList != null) {
			newMore = new ArrayList<>();
			morePoly = new ArrayList<>();
			newMore.add(morePoly);
		}
		Coord trailCoord = null;
		int trailPosn = 0, trailRel = 0;

		for (Coord leadCoord : coords) {
			int leadPosn = isLongitude ? leadCoord.getHighPrecLon() : leadCoord.getHighPrecLat();
			int leadRel = Integer.signum(leadPosn - dividingLine);
			if (trailCoord != null) { // use first point as trailing (poly is closed)

				Coord lineCoord = null;
				if (trailRel == 0) // trailing point on line
					lineCoord = trailCoord;
				else if (leadRel == 0) // leading point on line
					lineCoord = leadCoord;
				else if (trailRel != leadRel) { // crosses line; make intersecting coord
					int newOther = isLongitude ? trailCoord.getHighPrecLat() : trailCoord.getHighPrecLon();
					int leadOther = isLongitude ? leadCoord.getHighPrecLat() :  leadCoord.getHighPrecLon();
					if (newOther != leadOther)
						newOther += (dividingLine - trailPosn) * (leadOther - newOther) / (leadPosn - trailPosn);
					lineCoord = Coord.makeHighPrecCoord(isLongitude ? newOther : dividingLine, isLongitude ? dividingLine : newOther);
				}
				if (lineCoord != null && coordPool != null) {
					// Add new coords to pool. Also add existing ones if on the dividing line because there is slight
					// chance that the intersection will coincide with an existing point and ShapeMergeFilter expects
					// the opening/closing point to be the same object. If we see the original point first,
					// all is good, but if other way around, it will replace an original point with the created one.
					long hashVal = Utils.coord2Long(lineCoord);
					Coord replCoord = coordPool.get(hashVal);
					if (replCoord == null)
						coordPool.put(hashVal, lineCoord);
					else
						lineCoord = replCoord;
				}

				if (lessList != null) {
					if (leadRel < 0) { // this point required
						if (trailRel >= 0) // previous not on this side, add line point
							lessPoly.add(lineCoord);
						lessPoly.add(leadCoord);
					} else if (trailRel < 0) { // if this not reqd and prev was, add line point and start new shape
						lessPoly.add(lineCoord);
						lessPoly = new ArrayList<>();
						newLess.add(lessPoly);
					}
				}

				// identical to above except other way around
				if (moreList != null) {
					if (leadRel > 0) { // this point required
						if (trailRel <= 0) // previous not on this side, add line point
							morePoly.add(lineCoord);
						morePoly.add(leadCoord);
					} else if (trailRel > 0) { // if this not reqd and prev was, add line point and start new shape
						morePoly.add(lineCoord);
						morePoly = new ArrayList<>();
						newMore.add(morePoly);
					}
				}

			} // if not first Coord
			trailCoord = leadCoord;
			trailPosn = leadPosn;
			trailRel = leadRel;
		} // for leadCoord
		processLineList(newLess, lessList, isLongitude);
		processLineList(newMore, moreList, isLongitude);
	} // splitShape


	/**
	 * clip a shape with a rectangle
	 *
	 * Use above splitShape for each side; just keeping the 1/2 we want each time.
	 *
	 * @param coords the shape.
	 * @param bounds the clipping area.
	 * @param coordPool if not null, hashmap for created coordinates. Will all be on the edge.
	 * @return list of shapes.
	 */
	public static List<List<Coord>> clipToBounds(List<Coord> coords, Area bounds, Long2ObjectOpenHashMap<Coord> coordPool) {
		List<List<Coord>> newListA = new ArrayList<>();
		int dividingLine = bounds.getMinLat() << Coord.DELTA_SHIFT;
		splitShape(coords, dividingLine, false, null, newListA, coordPool);
		if (newListA.isEmpty())
			return newListA;
		List<List<Coord>> newListB = new ArrayList<>();
		dividingLine = bounds.getMinLong() << Coord.DELTA_SHIFT;
		for (List<Coord> aShape : newListA)
			splitShape(aShape, dividingLine, true, null, newListB, coordPool);
		if (newListB.isEmpty())
			return newListB;
		newListA.clear();
		dividingLine = bounds.getMaxLat() << Coord.DELTA_SHIFT;
		for (List<Coord> aShape : newListB)
			splitShape(aShape, dividingLine, false, newListA, null, coordPool);
		if (newListA.isEmpty())
			return newListA;
		newListB.clear();
		dividingLine = bounds.getMaxLong() << Coord.DELTA_SHIFT;
		for (List<Coord> aShape : newListA)
			splitShape(aShape, dividingLine, true, newListB, null, coordPool);
		return newListB;
	} // clipToBounds

} // ShapeSplitter
