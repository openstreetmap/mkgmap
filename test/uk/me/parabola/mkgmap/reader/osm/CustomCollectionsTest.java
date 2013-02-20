/*
 * Copyright (c) 2012.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 * 
 * Author: GerdP
 */

package uk.me.parabola.mkgmap.reader.osm;


import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 
 */
public class CustomCollectionsTest {

	@Test
	public void testOSMId2ObjectMap() {
		testMap(new OSMId2ObjectMap<Long>(), 0L);
		testMap(new OSMId2ObjectMap<Long>(), -10000L);
		testMap(new OSMId2ObjectMap<Long>(), 1L << 35);
		testMap(new OSMId2ObjectMap<Long>(), -1L << 35);
	}

	private void testMap(OSMId2ObjectMap<Long> map, long idOffset) {
		
		for (long i = 1; i < 1000; i++) {
			Long j = map.put(idOffset + i, new Long(i));
			assertEquals(j, null);
			assertEquals(map.size(), i);
		}

		for (long i = 1; i < 1000; i++) {
			boolean b = map.containsKey(idOffset + i);
			assertEquals(b, true);
		}


		for (long i = 1; i < 1000; i++) {
			assertEquals((Long)map.get(idOffset + i), new Long(i));
		}

		// random read access 
		for (long i = 1; i < 1000; i++) {
			Long key = (long) Math.max(1, (Math.random() * 1000));
			assertEquals((Long)map.get(idOffset + key), key);
		}

		for (long i = 1000; i < 2000; i++) {
			assertEquals((Long)map.get(idOffset + i), null);
		}
		
		for (long i = 1000; i < 2000; i++) {
			boolean b = map.containsKey(idOffset + i);
			assertEquals(b, false);
		}
		for (long i = 1000; i < 1200; i++) {
			Long j = map.put(idOffset + i, new Long(333));
			assertEquals(j, null);
			assertEquals(map.size(), i);
		}
		// random read access 2 
		for (int i = 1; i < 1000; i++) {
			Long key = 1000 + (long) (Math.random() * 200);
			assertEquals((Long)map.get(idOffset + key), new Long(333));
		}


		for (long i = -2000; i < -1000; i++) {
			assertEquals(map.get(idOffset + i), null);
		}
		for (long i = -2000; i < -1000; i++) {
			boolean b = map.containsKey(idOffset + i);
			assertEquals(b, false);
		}
		long mapSize = map.size();
		// seq. update existing records 
		for (int i = 1; i < 1000; i++) {
			long j = map.put(idOffset + i, new Long (i+333));
			assertEquals(j, i);
			assertEquals(map.size(), mapSize);
		}
		// random read access 3, update existing entries 
		for (int i = 1; i < 1000; i++) {
			long j = map.put(idOffset + i, new Long (i+555));
			assertEquals(true, j == i+333 | j == i+555);
			assertEquals(map.size(), mapSize);
		}
				
		assertEquals(map.get(idOffset + 123456), null);
		map.put(idOffset + 123456, (long) 999);
		assertEquals((Long)map.get(idOffset + 123456), new Long(999));
		map.put(idOffset + 123456, (long) 888);
		assertEquals((Long)map.get(idOffset + 123456), new Long(888));

		assertEquals(map.get(idOffset - 123456), null);
		map.put(idOffset - 123456, (long) 999);
		assertEquals((Long)map.get(idOffset - 123456), new Long(999));
		map.put(idOffset - 123456, (long) 888);
		assertEquals((Long)map.get(idOffset - 123456), new Long(888));
	
		map.clear();
		assertEquals(map.size(), 0);
		for (long i = 0; i < 100; i++){
			map.put(idOffset + i, new Long(i));
		}
		Long old = map.remove(idOffset + 5);
		assertEquals(old, new Long(5));
		assertEquals(map.size(), 99);
		assertEquals(map.get(idOffset + 5), null);
	}
	
}
