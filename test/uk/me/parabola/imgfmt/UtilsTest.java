/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * 
 * Author: Steve Ratcliffe
 * Create date: 30-Nov-2008
 */
package uk.me.parabola.imgfmt;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Test;

import uk.me.parabola.imgfmt.app.Coord;


public class UtilsTest {

	/**
	 * Very simple test that the coord2Long method is working.
	 */
	@Test
	public void testCoord2Long() {
		HashMap<Long,Coord> map = new HashMap<>();
		Coord lowerLeft = new Coord(-89.0,-179.0); 
		Coord upperRight = new Coord(89.0,179.0); 
		for (int latHp = -10; latHp < 10; latHp++){
			for (int lonHp = -10; lonHp < 10; lonHp++){
				for (int k = 0; k < 3;k++){
					Coord co; 
					if (k == 0)
						co = Coord.makeHighPrecCoord(latHp, lonHp); 
					else if (k == 1)
						co = Coord.makeHighPrecCoord(latHp+lowerLeft.getHighPrecLat(), lonHp+lowerLeft.getHighPrecLon());
					else
						co = Coord.makeHighPrecCoord(latHp+upperRight.getHighPrecLat(), lonHp+upperRight.getHighPrecLon());
					long key = Utils.coord2Long(co);
					Coord old = map.put(key, co);
					assertTrue("key not unique", old==null);
				}
			}
		}
		
	}
}
