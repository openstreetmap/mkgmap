/*
 * Copyright (C) 2010.
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

package uk.me.parabola.mkgmap.reader.osm;

import java.util.concurrent.atomic.AtomicLong;

public class FakeIdGenerator {

	public static final long START_ID = 1L << 62;
	
	private static final AtomicLong fakeId = new AtomicLong(START_ID);

	/**
	 * Retrieves a unique id that can be used to fake OSM ids.
	 * 
	 * @return a unique id
	 */
	public static long makeFakeId() {
		return fakeId.incrementAndGet();
	}

	public static boolean isFakeId(long id) {
		return id >= START_ID;
	}
	
}
