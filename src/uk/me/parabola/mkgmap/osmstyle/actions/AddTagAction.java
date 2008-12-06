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
 * Create date: 15-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Add a tag, optionally changing it if it already exists.  The value that
 * the tag is set to can have replacements from the current tags.
 *
 * @author Steve Ratcliffe
 */
public class AddTagAction implements Action {
	private boolean modify;
	private final String tag;
	private final ValueBuilder value;

	// The tags used to build the value.
	private Element valueTags;

	/**
	 * Create an action to add the given tag and value.  If the tag
	 * already has a value then nothing is done.
	 */
	public AddTagAction(String tag, String value) {
		this.tag = tag;
		this.value = new ValueBuilder(value);
	}

	/**
	 * Create an action to add the given tag with a value.
	 * If the modify flag is true, then we change the tag if it
	 * already exists.
	 */
	public AddTagAction(String tag, String value, boolean modify) {
		this.modify = modify;
		this.tag = tag;
		this.value = new ValueBuilder(value);
	}

	public void perform(Element el) {
		String tv = el.getTag(tag);
		if (tv != null && !modify)
			return;

		Element values = valueTags!=null? valueTags: el;

		String newval = value.build(values);
		if (newval != null)
			el.addTag(tag, newval);
	}

	public void setValueTags(Element valueTags) {
		this.valueTags = valueTags;
	}
}
