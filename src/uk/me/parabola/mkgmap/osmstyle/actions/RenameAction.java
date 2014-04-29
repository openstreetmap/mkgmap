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
 * Renames a tag.  Specifically takes the value of the 'from' tag, sets
 * the value of the 'to' tag and removes the 'from' tag.
 * @author Steve Ratcliffe
 */
public class RenameAction implements Action {
	private final short from;
	private final short to;

	public RenameAction(String from, String to) {
		this.from = TagDict.getInstance().xlate(from);
		this.to = TagDict.getInstance().xlate(to);
	}

	public void perform(Element el) {
		String fromval = el.getTag(from);
		if (fromval != null) {
			el.addTag(to, fromval);
			el.deleteTag(from);
		}
	}
}
