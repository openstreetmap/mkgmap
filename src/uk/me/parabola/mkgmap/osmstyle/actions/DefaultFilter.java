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
 * Create date: 07-Dec-2008
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

/**
 * Provide a default value if there is not one present.
 * Do we really need this?
 * @author Steve Ratcliffe
 */
public class DefaultFilter extends ValueFilter {
	private final String def;

	public DefaultFilter(String d) {
		def = d;
	}

	public String doFilter(String value) {
		return value == null || value.length() == 0 ? def : value;
	}
}
