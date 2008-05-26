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
 * Create date: May 26, 2008
 */
package uk.me.parabola.mkgmap;

/**
 * An option or a key value pair.  Imutable class.
 */
public class Option {
	private final String option;
	private final String value;

	protected Option(String optval) {
		String[] v = optval.split("[=:]", 2);
		if (v.length > 1) {
			option = v[0].trim();
			value = v[1].trim();
		} else {
			option = optval;
			value = "";
		}
	}

	protected Option(String option, String value) {
		this.option = option;
		this.value = value;
	}

	public String getOption() {
		return option;
	}

	public String getValue() {
		return value;
	}
}
