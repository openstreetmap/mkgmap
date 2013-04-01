/*
 * Copyright (C) 2012.
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

package uk.me.parabola.mkgmap.osmstyle.function;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Abstract implementation of a style function that is able
 * to cache the function values.
 * @author WanMil
 */
public abstract class CachedFunction extends StyleFunction {

	public CachedFunction(String value) {
		super(value);
	}

	public final String value(Element el) {
		// check if the element type is supported by this function
		if (el instanceof Node ) {
			if (supportsNode() == false) {
				return null;
			}
		} else if (el instanceof Way) {
			if (supportsWay() == false) {
				return null;
			}
		} else  if (el instanceof Relation) {
			if (supportsRelation() == false) {
				return null;
			}
		}
		
		if (isCached()) {
			// if caching is supported check if the value has already
			// been calculated
			String cachedValue = el.getTag(getCacheTag());
			if (cachedValue != null) {
				return cachedValue;
			}
		}

		// calculate the function value
		String functionResult = calcImpl(el);
		
		if (isCached()) {
			// if caching is supported save the value for later usage
			el.addTag(getCacheTag(), functionResult);
		}

		return functionResult;
	}
	
	/**
	 * This method contains the real calculation of the function value and must be 
	 * implemented by subclasses.
	 * @param el the function parameter
	 * @return the function value
	 */
	protected abstract String calcImpl(Element el);

	/**
	 * Retrieves the tag name that is used to cache the function value to 
	 * avoid multiple calculations for the same element. 
	 * @return tag name used for caching
	 */
	protected String getCacheTag() {
		return "mkgmap:cache_"+getName();
	}
	
	public boolean isCached() {
		return true;
	}
	
}
