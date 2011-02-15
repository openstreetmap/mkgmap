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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the sorting positions for all the characters in a codepage.
 * @author Steve Ratcliffe
 */
public class Sort {

	private static final byte[] ZERO_KEY = new byte[3];

	private int codepage;
	private int id1; // Unknown - identifies the sort
	private int id2; // Unknown - identifies the sort

	private String description;
	private Charset charset;

	private final byte[] primary = new byte[256];
	private final byte[] secondary = new byte[256];
	private final byte[] tertiary = new byte[256];
	private final byte[] flags = new byte[256];
	private final List<Character> tab2 = new ArrayList<Character>();
	private CharsetEncoder encoder;

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

	/**
	 * Create a sort key for a given unicode string.  The sort key can be compared instead of the original strings
	 * and will compare based on the sorting represented by this Sort class.
	 *
	 * Using a sort key is more efficient if many comparisons are being done (for example if you are sorting a
	 * list of strings).
	 *
	 *
	 * @param object This is saved in the sort key for later retrieval and plays no part in the sorting.
	 * @param s The string for which the sort key is to be created.
	 * @param second Secondary sort key.
	 * @return A sort key.
	 */
	public <T> SortKey<T> createSortKey(T object, String s, int second) {
		CharBuffer inb = CharBuffer.wrap(s);
		try {
			ByteBuffer out = encoder.encode(inb);
			byte[] bval = out.array();
			byte[] key = new byte[bval.length * 3 + 3];
			int length = bval.length;
			for (int i = 0; i < length; i++) {
				byte b = bval[i];
				key[i] = primary[b & 0xff];
				key[length + 1 + i] = secondary[b & 0xff];
				key[2*length + 2 + i] = tertiary[b & 0xff];
			}
			key[length] = 0;
			key[2 * length + 1] = 0;
			key[3 * length + 2] = 0;
			return new SrtSortKey<T>(object, key, second);
		} catch (CharacterCodingException e) {
			return new SrtSortKey<T>(object, ZERO_KEY);
		}
	}

	public <T> SortKey<T> createSortKey(T object, String s) {
		return createSortKey(object, s, 0);
	}

	public byte getPrimary(int ch) {
		return primary[ch];
	}

	public byte getSecondary(int ch) {
		return secondary[ch];
	}

	public byte getTertiary(int ch) {
		return tertiary[ch];
	}

	public byte getFlags(int ch) {
		return flags[ch];
	}

	public List<Character> getTab2() {
		return tab2;
	}

	public int getCodepage() {
		return codepage;
	}

	public Charset getCharset() {
		return charset;
	}

	public int getId1() {
		return id1;
	}

	public void setId1(int id1) {
		this.id1 = id1;
	}

	public int getId2() {
		return id2;
	}

	public void setId2(int id2) {
		this.id2 = id2;
	}

	public void setCodepage(int codepage) {
		this.codepage = codepage;
		charset = Charset.forName("cp" + codepage);
		encoder = charset.newEncoder();
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Create a default sort that simply sorts by the values of the characters.
	 *
	 * @return A default sort.
	 */
	public static Sort defaultSort() {
		Sort sort = new Sort();
		for (int i = 1; i < 256; i++) {
			sort.add(i, i, 0, 0, 0);
		}
		sort.charset = Charset.forName("ascii");
		sort.encoder = sort.charset.newEncoder();
		sort.setDescription("Default sort");
		return sort;
	}
}
