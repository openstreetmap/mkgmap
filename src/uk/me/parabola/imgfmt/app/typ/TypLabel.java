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
package uk.me.parabola.imgfmt.app.typ;

/**
 * @author Steve Ratcliffe
 */
public class TypLabel {
	private final int lang;
	private final String text;

	public TypLabel(String in) {
		String[] split = in.split(",", 2);
		int l;
		String s;
		try {
			l = Integer.decode(split[0]);
			s = split[1];
		} catch (NumberFormatException e) {
			l = 0;
			s = in;
		}
		this.lang = l;
		this.text = s;
	}

	public TypLabel(int lang, String text) {
		this.lang = lang;
		this.text = text;
	}

	public int getLang() {
		return lang;
	}

	public String getText() {
		return text;
	}
}
