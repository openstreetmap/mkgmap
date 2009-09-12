/*
 * Copyright 2009 Toby Speight
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package uk.me.parabola.mkgmap.osmstyle.actions;

/**
 * A <code>HeightFilter</code> transforms values into Garmin-tagged elevations.
 *
 * @author Toby Speight
 *
 * @since 2009-04-26
 */
public class HeightFilter extends ConvertFilter {

    public HeightFilter(String s) {
		super(s);
    }

	public String doFilter(String value) {
		String s = super.doFilter(value);
		if (s != null)
			s = "\u001f" + s;
		return s;
	}
}
