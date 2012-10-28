/*
 * Copyright (C) 2008-2012 Steve Ratcliffe
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
 * Create date: 11-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import java.util.regex.Pattern;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Regular expression matching.
 * @author Steve Ratcliffe
 */
public class RegexOp extends AbstractBinaryOp {
	private Pattern pattern;

	public RegexOp() {
		setType(REGEX);
	}

	public boolean eval(Element el) {
		String tagval = getTagValue(el, getFirst().value());
		if (tagval == null)
			return false;

		return pattern.matcher(tagval).matches();
	}

	public int priority() {
		return 10;
	}

	public void setSecond(Op second) {
		assert second.isType(VALUE);
		super.setSecond(second);
		pattern = Pattern.compile(second.value());
	}
}
