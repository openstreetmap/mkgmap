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

package uk.me.parabola.imgfmt.app.srt;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the sorting positions for all the characters in a codepage.
 * @author Steve Ratcliffe
 */
public class Sort {

	private final byte[] primary = new byte[256];
	private final byte[] secondary = new byte[256];
	private final byte[] tertiary = new byte[256];
	private final byte[] flags = new byte[256];
	private final List<Character> tab2 = new ArrayList<Character>();

	public void add(int ch, int primary, int secondary, int tertiary, int flags) {
		this.primary[ch & 0xff] = (byte) primary;
		this.secondary[ch & 0xff] = flags > 0xf? 0: (byte) secondary;
		this.tertiary[ch & 0xff] = flags > 0xf? 0: (byte) tertiary;
		this.flags[ch & 0xff] = (byte) flags;
	}

	public void add(char tab2) {
		this.tab2.add(tab2);
	}

	/**
	 * Return a table indexed by a character value in the target codepage, that gives the complete sort
	 * position of the character.
	 * @return A table of sort positions.
	 */
	public char[] getSortPositions() {
		char[] tab = new char[256];

		for (int i = 1; i < 256; i++) {
			tab[i] = (char) (((primary[i] << 8) & 0xff00) | ((secondary[i] << 4) & 0xf0) | (tertiary[i] & 0xf));
		}

		return tab;
	}

	public SortKey createSortKey(String s) {
		return null;
	}
	byte[] getPrimary() {
		byte[] tab = new byte[256];
		System.arraycopy(primary, 0, tab, 0, 256);
		return tab;
	}
	byte[] getSecondary() {
		byte[] tab = new byte[256];
		System.arraycopy(secondary, 0, tab, 0, 256);
		return tab;
	}
	byte[] getTertiary() {
		byte[] tab = new byte[256];
		System.arraycopy(tertiary, 0, tab, 0, 256);
		return tab;
	}
	
	public byte[] getFlags() {
		return flags;
	}

	public List<Character> getTab2() {
		return tab2;
	}
}
