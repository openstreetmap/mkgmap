/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.scan;

/**
 * When reading a word you can save the text and if it was quoted or not.
 *
 * @author Steve Ratcliffe
 */
public class WordInfo {
	private final String text;
	private final boolean quoted;

	public WordInfo(String text, boolean quoted) {
		this.text = text;
		this.quoted = quoted;
	}

	public String getText() {
		return text;
	}

	public boolean isQuoted() {
		return quoted;
	}
}
