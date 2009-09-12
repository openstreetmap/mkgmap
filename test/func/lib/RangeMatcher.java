/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 01-Jul-2009 */
package func.lib;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * Test for a range of values around the expected one.  This allows
 * small changes without having to fix the test every time there is a
 * small change to the output size.
 * 
 * @author Steve Ratcliffe
 */
public class RangeMatcher extends BaseMatcher<Integer> {
	private final int minVal;
	private final int maxVal;

	public RangeMatcher(int size) {
		this(size, size/20+1);
	}

	public RangeMatcher(int size, int range) {
		this.minVal = size - range;
		this.maxVal = size + range;
	}

	public boolean matches(Object o) {
		int other = (Integer) o;
		if (other > minVal && other < maxVal)
			return true;
		else return false;
	}

	public void describeTo(Description description) {
		description.appendValueList("between ", " and ", "", minVal, maxVal);
	}
}
