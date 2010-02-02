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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;


public class ElementTest {
	/*
	 * Test the iterator.
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
		assertEquals("list size", 3, l.size());

		Object[] observeds = l.toArray();
		Arrays.sort(observeds);
		String[] res = {"a=1", "b=2", "c=3"};
		Arrays.sort(res);
		assertArrayEquals("list includes wildcards", res, observeds);
	}

	@Test
	public void testEntryIterator() throws Exception {
		Element el = new Way(1);

		el.addTag("a", "1");
		el.addTag("b", "2");
		el.addTag("c", "3");

		List<String> keys = new ArrayList<String>();
		List<String> values = new ArrayList<String>();

		for (Map.Entry<String, String> ent : el.getEntryIteratable()) {
			keys.add(ent.getKey());
			values.add(ent.getValue());
		}

		Collections.sort(keys);
		Collections.sort(values);

		assertArrayEquals("list of keys",
				new String[] {"a", "b", "c"},
				keys.toArray());

		assertArrayEquals("list of values",
				new String[] {"1", "2", "3"},
				values.toArray());
	}
}
