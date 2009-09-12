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
 * 	This is multimap to store city information for the Address Locator
 *
 *
 * Author: Bernhard Heibler
 * Create date: 02-Jan-2009
 */

package uk.me.parabola.mkgmap.general;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MapPointMultiMap{

	private final Map<String,ArrayList<MapPoint>> map  = new HashMap<String, ArrayList<MapPoint>>();

	public MapPoint put(String name, MapPoint p)
	{
		ArrayList<MapPoint> list;
		
		list = map.get(name);
		
		if(list == null){

		   list = new ArrayList<MapPoint>();
		   list.add(p);		
		   map.put(name, list);
		}
		else
		   list.add(p);
		
		return p;
	}

	public MapPoint get(String name)
	{
		ArrayList<MapPoint> list;
		
		list = map.get(name);
		
		if(list != null)		
			return list.get(0);
		else
			return null;
	}
	   
	public Collection<MapPoint> getList(String name)
	{
		return map.get(name);
	}
}
