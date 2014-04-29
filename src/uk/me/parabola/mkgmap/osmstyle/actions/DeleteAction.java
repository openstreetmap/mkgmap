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
 * Create date: 29-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.TagDict;

/**
 * Deletes a tag.
 * 
 * @author Steve Ratcliffe
 */
public class DeleteAction implements Action {
	private final short tag;

	public DeleteAction(String tag) {
		this.tag = TagDict.getInstance().xlate(tag);
	}

	public void perform(Element el) {
		el.deleteTag(tag);
	}

	public String toString() {
		return "delete " + TagDict.getInstance().get(tag) + ";";
	}
}