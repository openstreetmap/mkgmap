/*
 * Copyright (C) 2009 Bernhard Heibler
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
 * 	This is multi-map to store city information for the Address Locator
 *  tt provides also a fast tile based nearest point search function
 *
 *
 * Author: Bernhard Heibler
 * Create date: 02-Jan-2009
 */

package uk.me.parabola.mkgmap.general;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;

public class MapPointFastFindMap{

	private final Map<String, ArrayList<MapPoint>> map  = new HashMap<String, ArrayList<MapPoint>>();
	private final Map<Long,ArrayList<MapPoint>> posMap = new HashMap<Long,ArrayList<MapPoint>>();
	private final ArrayList<MapPoint> points  =  new ArrayList<MapPoint>();

	private static final  long POS_HASH_DIV = 8000;  	// the smaller -> more tiles 
	private static final  long POS_HASH_MUL = 10000;	// multiplier for latitude to create hash

	public MapPoint put(String name, MapPoint p)
	{
		ArrayList<MapPoint> list;
		
		if(name != null)
		{
			list = map.get(name);
		
			if(list == null){

				list = new ArrayList<MapPoint>();
				list.add(p);		
				map.put(name, list);
			}
			else
				list.add(p);
			
			points.add(p);
		}
		
		long posHash  =  getPosHashVal(p.getLocation().getLatitude(), p.getLocation().getLongitude());
	
		list = posMap.get(posHash);
		
		if(list == null)
		{
			list = new ArrayList<MapPoint>();
			list.add(p);		
			posMap.put(posHash, list);
		}
		else
			list.add(p);
		
		return p;
	}

	public MapPoint get(String name) {
		ArrayList<MapPoint> list = map.get(name);

		if(list != null)		
			return list.get(0);
		else
			return null;
	}
	   
	public Collection<MapPoint> getList(String name)
	{
		return map.get(name);
	}
	   
	public long size()
	{
		return points.size();
	}


	public MapPoint findNextPoint(MapPoint p)
	{
		/* tile based search 
			
		to prevent expensive linear search over all points we put the points
		into tiles. We just search the tiles the point is in linear and the 
		surrounding tiles. If we don't find a point we have to search further
		around the central tile

		*/

		MapPoint nextPoint = null;
		
		if(posMap.size() < 1)  // No point in list
		   return nextPoint;
		
		long centLatitIdx = p.getLocation().getLatitude()  / POS_HASH_DIV ;
		long centLongiIdx = p.getLocation().getLongitude() / POS_HASH_DIV ;
		long delta = 1;

		double minDist = Double.MAX_VALUE;
		do
		{
			// in the first step we only check our tile and the tiles surrounding us
			
			for(long latitIdx = centLatitIdx - delta; latitIdx <= centLatitIdx + delta; latitIdx++)
		    for(long longiIdx = centLongiIdx - delta; longiIdx <= centLongiIdx + delta; longiIdx++)
		    {
		    	if(delta < 2 
						|| latitIdx == centLatitIdx - delta 
						|| latitIdx == centLatitIdx + delta
						|| longiIdx == centLongiIdx - delta
						|| longiIdx == centLongiIdx + delta)
					{

						long posHash = latitIdx * POS_HASH_MUL + longiIdx;

						ArrayList<MapPoint> list = posMap.get(posHash);

						if(list != null)
						{
			    
							for (MapPoint actPoint: list)
							{
								double distance =  actPoint.getLocation().distanceInDegreesSquared(p.getLocation());

								if(distance < minDist)
								{
									nextPoint = actPoint;
									minDist = distance;
									
								}
							}
						}
					}
			}
			delta ++; // We have to look in tiles fairer away
		}
		while(nextPoint == null); 
	 
		return nextPoint;
	}
	
	public MapPoint findPointInShape(MapShape shape, int pointType, String poiName) {
		List<Coord>	points = shape.getPoints();
		MapPoint nextPoint = null;
				
		if(posMap.size() < 1)  // No point in list
			return nextPoint;

		long lastHashValue = -1;
		for (Coord point : points) {
			long posHash = getPosHashVal(point.getLatitude(), point.getLongitude());

			if (posHash == lastHashValue) // Have we already checked this tile ?
				continue;

			lastHashValue = posHash;

			ArrayList<MapPoint> list = posMap.get(posHash);

			if (list != null) {
				for (MapPoint actPoint : list) {
					boolean checkThisPoint = false;
					
					if (pointType == 0 || actPoint.getType() == pointType)
						checkThisPoint = true;
					
					if(MapPoint.isCityType(pointType) && actPoint.isCity()	&& 
							actPoint.getName() != null && poiName != null)
					{
						// Check for city name pois in that shape
						// Since the types might not be exactly the same we
						// check for all places pois with the same name
						
						checkThisPoint = actPoint.getName().equalsIgnoreCase(poiName);
					}
							
					if (checkThisPoint && shape.contains(actPoint.getLocation()))
						return actPoint;
				}
			}
		}
	
		return null;		
	}
	
	private long getPosHashVal(long lat, long lon)
	{
		long latitIdx  =  lat /  POS_HASH_DIV ;
		long longiIdx  =  lon /  POS_HASH_DIV ; 
		
		return latitIdx * POS_HASH_MUL + longiIdx;
	}
}
