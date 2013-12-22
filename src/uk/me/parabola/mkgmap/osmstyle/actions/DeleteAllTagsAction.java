/*
 * Copyright (C) 2013.
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
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;

/**
 * Delete all tags from an element. This is useful to stop its processing.
 * 
 * @author WanMil
 */
public class DeleteAllTagsAction implements Action {

	// as long as there is not deleteAll method copy the tags of element without tags
	private final Element noTagElement;
	
	public DeleteAllTagsAction() {
		this.noTagElement = new Node(0, new Coord(0,0));
	}

	public void perform(Element el) {
		// remove all tags by copying the tags from a no tag element
		el.copyTags(noTagElement);
	}

	public String toString() {
		return "deletealltags;";
	}
}