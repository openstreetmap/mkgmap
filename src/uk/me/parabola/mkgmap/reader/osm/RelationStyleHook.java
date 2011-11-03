/*
 * Copyright (C) 2011.
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

package uk.me.parabola.mkgmap.reader.osm;

import java.io.FileNotFoundException;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This hook applies the relation rules of the style system.
 * @author WanMil
 */
public class RelationStyleHook extends OsmReadingHooksAdaptor {

	private Style style;
	private ElementSaver saver;
	
	public RelationStyleHook() {
	}

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		
		String loc = props.getProperty("style-file");
		if (loc == null)
			loc = props.getProperty("map-features");
		String name = props.getProperty("style");

		if (loc == null && name == null)
			name = "default";

		try {
			this.style = new StyleImpl(loc, name);
			this.style.applyOptionOverride(props);

		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
			throw new ExitException("Could not open style " + name);
		} catch (FileNotFoundException e) {
			String name1 = (name != null)? name: loc;
			throw new ExitException("Could not open style " + name1);
		}

		return super.init(saver, props);
	}

	public void end() {
		Rule relationRules = style.getRelationRules();
		for (Relation rel : saver.getRelations().values()) {
			relationRules.resolveType(rel, TypeResult.NULL_RESULT);
		}
		super.end();
	}

	
	
}
