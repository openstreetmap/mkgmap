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
package uk.me.parabola.mkgmap.general;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;

/**
 * A class for clipping shapes using a given clipping rectangle.
 * @author GerdP
 *
 */
public class SutherlandHodgmanPolygonClipper {

	private static final int LEFT = 0;
	private static final int TOP = 1;
	private static final int RIGHT = 2;
	private static final int BOTTOM= 3;

	/** 
	 * Helper routine to make sure that we use identical objects for calculated
	 * intersections. 
	 * @param lat30 high precision latitude
	 * @param lon30 high precision longitude
	 * @param map map that contains all calculated coord instances, might be enlarged
	 * @return the coord instance
	 */
	private static Coord getCoordInstance(int lat30, int lon30, Long2ObjectOpenHashMap<Coord> map){
		Coord p = Coord.makeHighPrecCoord(lat30, lon30);
		Coord reusedCoord = map.get(Utils.coord2Long(p));
		if (reusedCoord == null){
			map.put(Utils.coord2Long(p), p);
			return p;
		}
		return reusedCoord;
	}
	
	
	//    The Sutherland-Hodgman algorithm Pseudo-Code: 	 
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
	 * Clip a single polygon with a given rectangle using the Sutherland-Hodgman algorithm.
	 * It uses the high precision coordinate values. 
	 * This is much faster compared to the area.intersect method, but may create dangling edges.
	 * The result list contains the original Coord instances where possible.  
	 * @param subjectPolygon
	 * @param clippingRect
	 * @param bbox
	 * @return a closed polygon or null
	 */
	public static List<Coord> clipPolygon (List<Coord> subjectPolygon, Area clippingRect) {
		if (subjectPolygon == null)
			return null;
		
		int n = subjectPolygon.size();
		if (subjectPolygon.get(0).highPrecEquals(subjectPolygon.get(n-1))){
			n--;
		}
		if (n < 3)
			return null;
		
		List<Coord> outputList = new ArrayList<>(subjectPolygon.subList(0, n));
		Coord[] input = new Coord[outputList.size()];
		Long2ObjectOpenHashMap<Coord> map = new Long2ObjectOpenHashMap<>();
		int pos = 0;
		int countPoints = outputList.size();
		if (countPoints < 3)
			return null;
		int leftX = clippingRect.getMinLong() * (1 << Coord.DELTA_SHIFT);
		int rightX = clippingRect.getMaxLong() * (1 << Coord.DELTA_SHIFT);
		int lowerY = clippingRect.getMinLat() * (1 << Coord.DELTA_SHIFT);
		int upperY = clippingRect.getMaxLat() * (1 << Coord.DELTA_SHIFT);
		boolean eIsIn = false, sIsIn = false;
		for (int side = LEFT; side <= BOTTOM; side ++){
			countPoints = outputList.size();
			if (countPoints < 3)
				return null; // ignore point or line 
			
			input = outputList.toArray(input);
			outputList.clear();
			boolean hasPointThatIsNotOnBoundary = false;
			int sLon30 = 0,sLat30 = 0;
			int iLon30 = 0,iLat30 = 0; // intersection
			pos = countPoints-1;
			for (int i = 0; i < countPoints+1; i++){
				if (pos >=  countPoints)
					pos = 0;
				Coord pointE = input[pos++];
				int eLon30 = pointE.getHighPrecLon();
				int eLat30 = pointE.getHighPrecLat();
				switch (side){
				case LEFT: eIsIn =  (eLon30 >= leftX); break;
				case TOP: eIsIn =  (eLat30 <= upperY); break;
				case RIGHT: eIsIn =  (eLon30 <= rightX); break;
				default: eIsIn =  (eLat30 >= lowerY); 
				}
				if (i > 0){
					if (eIsIn != sIsIn){
						// compute intersection
						double slope;
						if (eLon30 != sLon30)
							slope = (double)(eLat30 - sLat30) / (eLon30-sLon30);
						else slope = 1;
	
						switch (side){
						case LEFT: 
							iLon30 = leftX;
							iLat30 = (int) Math.round(slope *(leftX-sLon30)) + sLat30;
							break;
						case RIGHT: 
							iLon30 = rightX;
							iLat30 = (int) Math.round(slope *(rightX-sLon30)) + sLat30; 
							break;
						case TOP: 
							if (eLon30 != sLon30)
								iLon30 =  sLon30 + (int) Math.round((double)(upperY - sLat30) / slope);
							else 
								iLon30 =  sLon30;
							iLat30 = upperY;
							break;
						default: // BOTTOM
							if (eLon30 != sLon30)
								iLon30 =  sLon30 + (int) Math.round((double)(lowerY - sLat30) / slope);
							else 
								iLon30 =  sLon30;
							iLat30 = lowerY;
							break;
	
						}
					}
					Coord intersectionPoint = null;
					if (eIsIn){
						if (!sIsIn){
							intersectionPoint = getCoordInstance(iLat30, iLon30, map);
							if (intersectionPoint.highPrecEquals(pointE) == false){
								outputList.add(intersectionPoint);
							}
						}
						outputList.add(pointE);
						boolean eOnBoundary = false;
						switch (side){
						case LEFT: if (eLon30 == leftX) eOnBoundary = true; break; 
						case TOP: if (eLat30 == upperY) eOnBoundary = true; break; 
						case RIGHT: if(eLon30 == rightX) eOnBoundary = true; break; 
						default: if (eLat30 == lowerY) eOnBoundary = true; break; // case BOTTOM 
						}
						if (!eOnBoundary)
							hasPointThatIsNotOnBoundary = true;
					}
					else {
						if (sIsIn){
							if (iLat30 != sLat30 || iLon30 != sLon30){
								intersectionPoint = getCoordInstance(iLat30, iLon30, map);
								outputList.add(intersectionPoint);
							}
						}
					}
				}
				
				// S = E
				sLon30 = eLon30; sLat30 = eLat30;
				sIsIn = eIsIn;
				
			}
			if (!hasPointThatIsNotOnBoundary)
				return null;
		}
		if (outputList.size() < 3)
			return null;
		outputList.add(outputList.get(0));
		return outputList;
	}
}
