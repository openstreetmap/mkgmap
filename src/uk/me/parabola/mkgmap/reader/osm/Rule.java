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
 * Create date: 07-Nov-2008
 */
package uk.me.parabola.mkgmap.reader.osm;

/**
 * A rule takes an element and returns the correct garmin type for it.
 * Implementations can be simple or complex as needed.
 *
 * @author Steve Ratcliffe
 */
public interface Rule {

	/**
	 * Given the element return the garmin type that should be used to
	 * represent it.
	 *
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @param result The resolved Garmin type that will go into the map.
	 */
	public void resolveType(Element el, TypeResult result);
	
	/**
	 * 
	 * Given the element return the garmin type that should be used to
	 * represent it.
	 *
	 * @param cacheId
	 * @param el The element as read from an OSM xml file in 'tag' format.
	 * @param result The resolved Garmin type that will go into the map.
	 * @return
	 */
	public int resolveType(int cacheId, Element el, TypeResult result);
	
	/**
	 * Sets the finalize rules that are executed when 
	 * an element type definition matches.
	 * 
	 * @param finalizeRule finalize rule(s)
	 */
	public void setFinalizeRule(Rule finalizeRule);
	
	public void printStats(String header);

	public Rule getFinalizeRule();

	public boolean containsExpression(String exp);
	
}
