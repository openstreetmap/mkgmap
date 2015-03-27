/*
 * Copyright (C) 2012.
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

import static org.junit.Assert.assertFalse;
import org.junit.Test;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.util.KdTree;

public class KdTreeTest {

	@Test
	public void TestFindNextPoint(){
        KdTree<MapPoint> t = new KdTree<>( );
        
        int [][]test = {{70,20}, {50,40}, {90,60}, {20,30}, {40,70}, {80,10}, {-10,20}, {-30,-40} }  ;
        Coord []testCoords = new Coord[test.length]; 
        
        for( int i = 0; i < test.length; i++ )
        {
        	MapPoint p = new MapPoint();
        	testCoords[i] = new Coord(test[i][0],test[i][1]);
        	p.setLocation(testCoords[i]);
        	t.add(p);
        }
        // compare naive search result with kd--tree result
        MapPoint toFind = new MapPoint();
        for (int x = -100; x < 100; x++){
        	for (int y = -100; y < 100; y++){
        		Coord co = new Coord(x,y);
        		double minDist = Double.MAX_VALUE;

        		for (int i = 0; i<testCoords.length; i++){
        			Double dist =  testCoords[i].distanceInDegreesSquared(co);
        			if (dist < minDist){
        				minDist = dist;
        			}
        		}
        		toFind.setLocation(co);
        		MapPoint next = (MapPoint) t.findNextPoint(toFind);
    			double dist =  next.getLocation().distanceInDegreesSquared(co);
    			double delta = Math.abs(dist - minDist); 
    			// if this test fails because 
        		assertFalse("delta should be 0.0: " + delta, delta != 0.0);
        	}
        }
	}
}
