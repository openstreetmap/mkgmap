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

Eventually maybe can be used instead of some of the above and elsewhere
*/

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
	 * @param lineInfo list of lines.
	 * @param origList list of shapes to which we append new shapes.
	 */
	private static int doLines(int startInx, int endEnclosed, MergeCloseHelper addHolesToThis,
				   List<MergeCloseHelper> lineInfo, List<List<Coord>> origList) {
		int inx = startInx;
		final boolean calledFromHole = addHolesToThis == null;
		while (inx < lineInfo.size()) {
			MergeCloseHelper thisLine = lineInfo.get(inx);
			if (thisLine.highPoint > endEnclosed) // only do enclosed items
				break; // simple - fully enclosed
			if (thisLine.lowPoint == endEnclosed && thisLine.highPoint == endEnclosed) // consider carefully
				if (calledFromHole == (thisLine.areaOrHole == -1))
					break; // stop if same type
			inx = doLines(inx+1, thisLine.highPoint, calledFromHole ? thisLine : null, lineInfo, origList);
			if (calledFromHole) // handling list of shapes
				thisLine.closeAppend(origList, true);
			else // handling list of holes
				addHolesToThis.addHole(thisLine);
		}
		return inx;
	} // doLines

	/**
	 * Service routine for splitShape. Takes list of lines and appends distinct shapes
	 * @param lineInfo list of lines that start and end on the dividing line (or orig startPoint)
	 * @param origList list of shapes to which we append new shapes formed from above
	 * @param fullArea of orig polygon. used for sign and handling of last line segment
	 */
	private static void processLineList(List<MergeCloseHelper> lineInfo, List<List<Coord>> origList, long fullArea) {
		if (origList == null) // never wanted this side
			return;
		MergeCloseHelper firstLine = lineInfo.get(0);
		if (lineInfo.size() == 1) { // single shape that never crossed line
			if (!firstLine.points.isEmpty()) // all on this side
				firstLine.closeAppend(origList, false);
			return;
		}
		// look at last item in list of lines
		MergeCloseHelper lastLine = lineInfo.get(lineInfo.size()-1);
		if (lastLine.points.isEmpty()) // will be empty if started on other side
			lineInfo.remove(lineInfo.size()-1);
		else { // ended up on this side and must have crossed the line
			// so first element is really the end of the last
			lastLine.combineFirstIntoLast(firstLine, fullArea);
			lineInfo.remove(0);
			firstLine = lineInfo.get(0);
		}
		if (lineInfo.size() == 1) { // simple poly that crossed once and back
			firstLine.setMoreInfo(0);
			firstLine.closeAppend(origList, true);
			return;
		}
		// Above were the simple cases - probably 99% of calls.

		// splitShape has generated a list of lines that start and end on the dividing line.
		// These lines don't cross. Order them by their lowest point on the divider, but note which
		// direction they go. The first (and last) line must define a shape. Starting with this
		// shape; the next line up, if it is within this shape, must define a hole and
		// so is added to the list of points for the shape. For the hole, recurse to
		// handle any shapes enclosed. Repeat until we reach the end of the enclosing
		// space.

		final int fullAreaSign = Long.signum(fullArea);
		// check and set any missing directions based on signs of full/area
		boolean someDirectionsNotSet = false;
		int areaDirection = 0;
		String diagMsg = "";
 		for (MergeCloseHelper thisLine : lineInfo) {
			thisLine.setMoreInfo(fullAreaSign);
			if (thisLine.direction == 0)
				someDirectionsNotSet = true;
			else if (thisLine.areaToLine != 0) {
				int tmpAreaDirection = thisLine.direction * Long.signum(thisLine.areaToLine);
				if (areaDirection == 0)
					areaDirection = tmpAreaDirection;
				else if (areaDirection != tmpAreaDirection)
					diagMsg += "Direction/Area conflict.";
			}
		}
		if (someDirectionsNotSet) {
			if (areaDirection == 0)
				diagMsg += "Cant deduce direction/Area mapping.";
			else
				for (MergeCloseHelper thisLine : lineInfo)
					if (thisLine.direction == 0)
						thisLine.direction = areaDirection * Long.signum(thisLine.areaToLine);
		}
		if (diagMsg != "") {
			log.warn(diagMsg, "Probably self-intersecting polygon", fullAreaSign, someDirectionsNotSet, areaDirection);
			for (MergeCloseHelper thisLine : lineInfo) {
				log.warn(thisLine);
				if (log.isDebugEnabled())
					for (Coord co : thisLine.points)
						log.debug("line", co, co.getHighPrecLat(), co.getHighPrecLon());
			}
		}

		lineInfo.sort(null);

		int dummy = doLines(0, Integer.MAX_VALUE, null, lineInfo, origList);
		assert dummy == lineInfo.size();
	} // processLineList

	private static List<Coord> startLine(List<MergeCloseHelper> lineInfo) {
		MergeCloseHelper thisLine = new MergeCloseHelper();
		lineInfo.add(thisLine);
		return thisLine.points;
	} // startLine

	private static void openLine(List<MergeCloseHelper> lineInfo, Coord lineCoord, int lineAlong, long currentArea) {
		MergeCloseHelper thisLine = lineInfo.get(lineInfo.size()-1);
		thisLine.points.add(lineCoord);
		thisLine.firstPoint = lineAlong;
		thisLine.startingArea = currentArea;
	} // openLine

	private static List<Coord> closeLine(List<MergeCloseHelper> lineInfo, Coord lineCoord, int lineAlong, long currentArea) {
		MergeCloseHelper thisLine = lineInfo.get(lineInfo.size()-1);
		thisLine.points.add(lineCoord);
		thisLine.lastPoint = lineAlong;
		thisLine.endingArea = currentArea;
		return startLine(lineInfo);
	} // closeLine

	/**
	 * Helper class for splitShape. Holds information about line.
	 * Sorts array/list of itself according to lowest point on dividing line.
	 */
	private static class MergeCloseHelper implements Comparable<MergeCloseHelper> {

		List<Coord> points;
		int firstPoint, lastPoint;
		long startingArea, endingArea; // from runningArea
		int direction;
		int lowPoint, highPoint;
		long areaToLine;
		int areaOrHole; // +1/-1

		MergeCloseHelper() {
			points = new ArrayList<>();
		} // MergeCloseHelper

		public String toString() {
			return "fp=" + firstPoint + " lp=" + lastPoint + " area=" + areaToLine + " #=" + points.size() + " " +
				points.get(1).toOSMURL() + " " + points.get(points.size()/2).toOSMURL();
		} // toString

		void setMoreInfo(int fullAreaSign) {
			this.direction = Integer.signum(lastPoint - firstPoint);
			if (this.direction > 0) {
				this.lowPoint = firstPoint;
				this.highPoint = lastPoint;
			} else {
				this.lowPoint = lastPoint;
				this.highPoint = firstPoint;
			}
			this.areaToLine = this.endingArea - this.startingArea; // correct if closed
			// !!! also correct when close along the line, because would do:
//			this.areaToLine += (long)(lastPoint + firstPoint) * (dividingLine - dividingLine);
			this.areaOrHole = fullAreaSign * Long.signum(this.areaToLine);
		} // setMoreInfo

		void combineFirstIntoLast(MergeCloseHelper other, long fullArea) {
			this.points.addAll(other.points);
			this.lastPoint = other.lastPoint;
			this.endingArea = fullArea + other.endingArea;
		} // combineFirstIntoLast

		public int compareTo(MergeCloseHelper other) {
			int cmp = this.lowPoint - other.lowPoint;
			if (cmp != 0)
				return cmp;
			// for same lowPoint, sort highPoint other way around to enclose as much as possible
			cmp = other.highPoint - this.highPoint;
			if (cmp != 0)
				return cmp;
			// case where when have same start & end
			// return the shape as lower than the hole, to handle first
			cmp = other.areaOrHole - this.areaOrHole;
			if (cmp != 0)
				return cmp;
			log.warn("Lines hit divider at same points and have same area sign", "this:", this, "other:", other);
			// after this, don't think anthing else possible, but, for stability
			return this.direction - other.direction;
		} // compareTo

		void addHole(MergeCloseHelper other) {
			if (other.areaToLine == 0)
				return; // spike into this area. cf. closeAppend()
			// shapes must have opposite directions.
			if (this.direction == 0 && other.direction == 0)
				log.warn("Direction of shape and hole indeterminate; probably self-intersecting polygon", "this:", this, "other:", other);
			else if (this.direction != 0 && other.direction != 0 && this.direction == other.direction)
				log.warn("Direction of shape and hole conflict; probably self-intersecting polygon", "this:", this, "other:", other);
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
			this.areaToLine += other.areaToLine;
		} // addHole

		/**
		 * closes a shape and appends to list.
		 *
		 * If the shape starts and ends at the same point on the dividing line then
		 * there is no need to close it. Also check for and chuck a spike, which happens
		 * if there is a single point just across the dividing line and the two intersecting
		 * points ended up being the same or an edge runs back on itself exactly.
		 *
		 * @param origList list of shapes to which we append new shapes.
		 * @param onDividingLine if false, shape not cut so don't assume/care much about it
		 */
		void closeAppend(List<List<Coord>> origList, boolean onDividingLine) {
			final Coord firstCoord = points.get(0);
			final int lastPointInx = points.size()-1;
			if (firstCoord.highPrecEquals(points.get(lastPointInx))) { // by chance, ends up closed
				// There is no need to close the shape along the line, but am finding, for shapes that never crossed the
				// dividing line, quite a few that, after splitShapes has rotating the shape by 1, have first and last
				// points highPrecEquals but they are different objects.
				// This means that the original first and last were the same object, but the second and last were highPrecEquals!
				// If left like this, it might be flagged by ShapeMergeFilter.
				// NB: if no coordPool, likely to be different closing object anyway
				if (firstCoord != points.get(lastPointInx))
					points.set(lastPointInx, firstCoord); // quietly replace with first point
			} else
				points.add(firstCoord); // close it
			if (onDividingLine) { // otherwise just one shape untouched by chopping
/* this is quite expensive! and drastic if there is a problem
				assert Math.abs(this.areaToLine) == Math.abs(uk.me.parabola.mkgmap.filters.ShapeMergeFilter.calcAreaSizeTestVal(points))
					: "Split calcAreaSize differs";
// this is less drastic, only ever happens after SplitShape has already detected problem
				long stdFuncSize = uk.me.parabola.mkgmap.filters.ShapeMergeFilter.calcAreaSizeTestVal(points);
				if (Math.abs(this.areaToLine) != Math.abs(stdFuncSize))
					log.warn("Split calcAreaSize differs; probably self-intersecting polygon", stdFuncSize, this);
*/
				if (this.areaToLine == 0)
					return;
			}
			origList.add(points);
		} // closeAppend

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

		List<MergeCloseHelper> newLess = null, newMore = null;
		List<Coord> lessPoly = null, morePoly = null;
		if (lessList != null) {
			newLess = new ArrayList<>();
			lessPoly = startLine(newLess);
		}
		if (moreList != null) {
			newMore = new ArrayList<>();
			morePoly = startLine(newMore);
		}
		/**
		 * trailXxx variables are the previous coordinate information.
		 * leadXxx            are for the current coordinate
		 * lineXxx            are the crossing point
		 * where Xxx is Coord : Coord
		 *              Away  : position in plane at right angles to dividing line
		 *              Along : position in same plane as the dividing line
		 *              Rel   : -1/0/+1 depending on relationship of Away to dividing line
		 */
		Coord trailCoord = null;
		int trailAway = 0, trailAlong = 0, trailRel = 0;
		long runningArea = 0;

		for (Coord leadCoord : coords) {
			final int leadAway  = isLongitude ? leadCoord.getHighPrecLon() : leadCoord.getHighPrecLat();
			final int leadAlong = isLongitude ? leadCoord.getHighPrecLat() : leadCoord.getHighPrecLon();
			final int leadRel = Integer.signum(leadAway - dividingLine);
			if (trailCoord != null) { // use first point as trailing (poly is closed)

				Coord lineCoord = null;
				int lineAlong = trailAlong; // initial assumption
				if (trailRel == 0) // trailing point on line
					lineCoord = trailCoord;
				else if (leadRel == 0) { // leading point on line
					lineCoord = leadCoord;
					lineAlong = leadAlong;
				} else if (trailRel != leadRel) { // crosses line; make intersecting coord
					if (lineAlong != leadAlong)
						lineAlong += Math.round((double)(dividingLine - trailAway) * (leadAlong - trailAlong)
										  / (leadAway - trailAway));
					lineCoord = Coord.makeHighPrecCoord(isLongitude ? lineAlong : dividingLine, isLongitude ? dividingLine : lineAlong);
				}
				if (lineCoord != null && coordPool != null) {
					// Add new coords to pool. Also add existing ones if on the dividing line because there is slight
					// chance that the intersection will coincide with an existing point and ShapeMergeFilter expects
					// the opening/closing point to be the same object. If we see the original point first,
					// all is good, but if other way around, it will replace an original point with the created one.
					final long hashVal = Utils.coord2Long(lineCoord);
					final Coord replCoord = coordPool.get(hashVal);
					if (replCoord == null)
						coordPool.put(hashVal, lineCoord);
					else
						lineCoord = replCoord;
				}

				long extraArea; // add in later to get the area to leading point
				if (leadRel * trailRel >= 0) // doesn't cross the line
					extraArea = (long)(trailAlong + leadAlong) * (trailAway - leadAway);
				else { // calc as 2 points
					runningArea += (long)(trailAlong + lineAlong) * (trailAway - dividingLine);
					extraArea = (long)(lineAlong + leadAlong) * (dividingLine - leadAway);
				}

				if (lessList != null) {
					if (leadRel < 0) { // this point required
						if (trailRel >= 0) // previous not on this side, add line point
							openLine(newLess, lineCoord, lineAlong, runningArea);
						lessPoly.add(leadCoord);
					} else if (trailRel < 0) // if this not reqd and prev was, add line point and start new shape
						lessPoly = closeLine(newLess, lineCoord, lineAlong, runningArea + (leadRel == 0 ? extraArea : 0));
				}

				// identical to above except other way around
				if (moreList != null) {
					if (leadRel > 0) { // this point required
						if (trailRel <= 0) // previous not on this side, add line point
							openLine(newMore, lineCoord, lineAlong, runningArea);
						morePoly.add(leadCoord);
					} else if (trailRel > 0) // if this not reqd and prev was, add line point and start new shape
						morePoly = closeLine(newMore, lineCoord, lineAlong, runningArea + (leadRel == 0 ? extraArea : 0));
				}

				runningArea += extraArea;
			} // if not first Coord
			trailCoord = leadCoord;
			trailAway = leadAway;
			trailAlong = leadAlong;
			trailRel = leadRel;
		} // for leadCoord
		processLineList(newLess, lessList, runningArea);
		processLineList(newMore, moreList, runningArea);
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
