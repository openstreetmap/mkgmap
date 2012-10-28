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
 * Create date: 06-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Tests for the existence of a tag.  Return true if the tag exists, regardless
 * of value.
 * 
 * @author Steve Ratcliffe
 */
public class ExistsOp extends AbstractOp {
	private String key;

	public ExistsOp() {
		setType(EXISTS);
	}

	public void setFirst(Op first) {
		super.setFirst(first);
		key = first.value();
	}

	public boolean eval(Element el) {
		return getTagValue(el, key) != null;
	}

	public int priority() {
		return 10;
	}

	public String getTypeString() {
		return "=*";
	}

	public String toString() {
		return key + "=*";
	}
}
