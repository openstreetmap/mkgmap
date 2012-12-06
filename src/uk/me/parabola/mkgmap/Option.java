/*
 * Copyright (C) 2008, 2011.
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
package uk.me.parabola.mkgmap;

/**
 * An option or a key value pair.  Immutable class.
 */
public class Option {
	private final String option;
	private final String value;
	private final boolean experimental;
	private final boolean reset;

	protected Option(String optval) {
		String[] v = optval.split("[=:]", 2);

		String name;
		String val;

		if (v.length > 1) {
			name = v[0].trim();
			val = v[1].trim();
		} else {
			name = optval;
			val = "";
		}

		boolean reset = false;
		if (name.startsWith("no-")) {
			reset = true;
			name = name.substring(3);
			val = null;
		}

		boolean exp = false;
		if (name.startsWith("x-")) {
			exp = true;
			name = name.substring(2);
		}

		option = name;
		value = val;
		experimental = exp;
		this.reset = reset;
	}

	protected Option(String option, String value) {
		this(option + '=' + value);
	}

	public String getOption() {
		return option;
	}

	public String getValue() {
		return value;
	}

	public boolean isExperimental() {
		return experimental;
	}

	public boolean isReset() {
		return reset;
	}
}
