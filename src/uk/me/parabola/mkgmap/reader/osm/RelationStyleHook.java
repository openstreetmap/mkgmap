/*
 * Copyright (C) 2011 - 2012.
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

import java.util.List;

import uk.me.parabola.mkgmap.build.LocatorUtil;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This hook applies the relation rules of the style system.
 * @author WanMil
 */
public class RelationStyleHook extends OsmReadingHooksAdaptor {

	private Style style;
	private ElementSaver saver;
	List<String> nameTagList;

	public RelationStyleHook() {
	}

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		nameTagList = LocatorUtil.getNameTags(props);
		return super.init(saver, props);
	}

	public void setStyle(Style style){
		this.style = style;
	}
	
	public void end() {
		Rule relationRules = style.getRelationRules();
		for (Relation rel : saver.getRelations().values()) {
			if (nameTagList != null){
				for (String t : nameTagList) {
					String val = rel.getTag(t);
					if (val != null) {
						rel.addTag("name", val);
						break;
					}
				}
			}			
			relationRules.resolveType(rel, TypeResult.NULL_RESULT);
			if (rel instanceof RestrictionRelation){
				((RestrictionRelation) rel).eval(saver.getBoundingBox());
			}
		}
		super.end();
	}

	
	
}
