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
package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;
import org.junit.Test;


public class ElementTest {
	/*
	 * Test the iterator.  It is kind of special in that it iterates over
	 * strings which are "key=value" and also the wildcard version "key=*".
	 */
	@Test
	public void testIterator() {
		Element el = new Way(1);

		el.addTag("a", "1");
		el.addTag("b", "2");
		el.addTag("c", "3");

		Collection<String> l = new ArrayList<String>();
		for (String s : el) {
			l.add(s);
		}
		assertEquals("list includes wildcards", 6, l.size());

		Object[] observeds = l.toArray();
		Arrays.sort(observeds);
		String[] res = {"a=1", "a=*", "b=2", "b=*", "c=3", "c=*"};
		Arrays.sort(res);
		assertArrayEquals("list includes wildcards", res, observeds);
	}
}
