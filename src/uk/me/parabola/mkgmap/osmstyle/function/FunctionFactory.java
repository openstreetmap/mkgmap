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

/**
 * A factory for style functions. 
 * @author WanMil
 */
public class FunctionFactory {

	/**
	 * Returns a new instance of a style function with the given name.
	 *
	 * @param name the style function name
	 * @return the style function instance or {@code null} if there is no such function
	 */
	public static StyleFunction createFunction(String name) {
		if ("length".equals(name))
			return new LengthFunction();
		//} else if ("get_tag".equals(name))
		//	return new GetTagFunction(tag);
		if ("is_closed".equals(name)) {
			return new IsClosedFunction();
		}
		if ("is_complete".equals(name)) {
			return new IsCompleteFunction();
		}
		return null;
	}
}
