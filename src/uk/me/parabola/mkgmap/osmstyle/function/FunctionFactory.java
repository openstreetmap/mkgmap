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

import java.util.HashMap;
import java.util.Map;

/**
 * A factory for style functions. 
 * @author WanMil
 */
public class FunctionFactory {

	// cache for style functions
	// use a thread local so that there is one cache per thread to ensure thread safety of the StyleFunction objects
	private static final ThreadLocal<Map<String, StyleFunction>> cache = new ThreadLocal<Map<String, StyleFunction>>() {
		protected Map<String, StyleFunction> initialValue() {
			return new HashMap<String, StyleFunction>();
		}
	};

	/**
	 * Retrieves a style function with the given name. The cache stores one instance
	 * per thread only to ensure thread safety. 
	 * @param name the style function name 
	 * @return the style function instance or <code>null</code> if there is no such function
	 */
	public static StyleFunction getCachedFunction(String name) {
		// check if the cache contains a function with the given name
		StyleFunction function = cache.get().get(name);
		
		if (function == null) {
			// the function need to be instantiated
			function = createFunction(name);
			
			// put the function into the cache for later usage
			if (function != null) {
				cache.get().put(name, function);
			}
		}
		
		return function;
	}
	
	
	/**
	 * Returns a new instance of a style function with the given name.
	 * @param name the style function name 
	 * @return the style function instance or <code>null</code> if there is no such function
	 */
	public static StyleFunction createFunction(String name) {
		if ("length".equals(name)) {
			return new LengthFunction();
		}
		if ("is_closed".equals(name)) {
			return new IsClosedFunction();
		}
		if ("is_complete".equals(name)) {
			return new IsCompleteFunction();
		}
		return null;
	}
}
