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

import java.util.HashSet;
import java.util.Set;

import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This hook performs some steps required for routing. <br/>
 * At the moment the only function is to add tags used for routing to the
 * overall tag list. The rest of the routing work is done in the 
 * {@link StyledConverter} class.
 * @author WanMil
 *
 */
public class RoutingHook extends OsmReadingHooksAdaptor {

	private final Set<String> usedTags;
	
	public RoutingHook() {
		usedTags = new HashSet<String>();
		usedTags.add("except");
		usedTags.add("restriction");
		usedTags.add("restriction:foot");
		usedTags.add("restriction:hgv");
		usedTags.add("restriction:motorcar");
		usedTags.add("restriction:vehicle");
		usedTags.add("restriction:motor_vehicle");
		usedTags.add("restriction:bicycle");
		usedTags.add("restriction:bus");
	}

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		
		if (props.getProperty("old-style", false)) {
			// the access tags need to be loaded if the old style handling
			// is active and access restrictions are handled by the java
			// source code and not by the style
			usedTags.add("access");
			usedTags.add("bicycle");
			usedTags.add("carpool");
			usedTags.add("delivery");
			usedTags.add("emergency");
			usedTags.add("foot");
			usedTags.add("goods");
			usedTags.add("hgv");
			usedTags.add("motorcar");
			usedTags.add("motorcycle");
			usedTags.add("psv");
			usedTags.add("route");
			usedTags.add("taxi");
		}
		int admLevelNod3 = props.getProperty("add-boundary-nodes-at-admin-boundaries", 2);
		if (admLevelNod3 > 0) {
			usedTags.add("boundary");
			usedTags.add("admin_level");
		}

		
		// only enabled if the route option is set
		return props.containsKey("route");
	}


	public Set<String> getUsedTags() {
		return usedTags;
	}
	

}
