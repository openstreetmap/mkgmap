/*
 * Copyright (C) 2009 
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
 * Author: Richard Fairhurst
 * Create date: 29-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Sends a message to the console.
 * 
 * @author Richard Fairhurst
 */
public class EchoAction implements Action {
	private final ValueBuilder value;

	public EchoAction(String str) {
		this.value = new ValueBuilder(str, false);
	}

	public void perform(Element el) {
		String e = value.build(el, el);
		System.err.println(el.getId() + ": " + e);
	}
}
